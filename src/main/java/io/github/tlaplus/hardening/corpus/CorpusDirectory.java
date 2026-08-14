package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Owns the on-disk layout and integrity checks for one FuzzTLA corpus. */
public final class CorpusDirectory {
    public static final String CONFIG_FILE_NAME = "config.toml";
    public static final String INPUT_DIRECTORY_NAME = "00-inputs";
    public static final String PARSER_PASS_DIRECTORY_NAME = "01parser-pass";
    public static final String PARSER_FAIL_DIRECTORY_NAME = "01parser-fail";
    public static final String PARSER_CRASH_DIRECTORY_NAME = "01parser-crash";
    public static final String PARSER_CRASH_REPORT_EXTENSION = ".stacktrace";

    private static final String WORK_DIRECTORY_NAME = ".work";
    private static final String PARSER_SCRATCH_DIRECTORY_NAME = "parser-tmp";
    private static final String LOCK_FILE_NAME = ".workflow.lock";
    private static final String PARSER_STAGE_NAME = "parser";
    private static final Pattern INPUT_FILE_NAME = Pattern.compile("([0-9a-f]{64})\\.cbor");
    private static final Pattern PARSER_CRASH_REPORT_FILE_NAME =
            Pattern.compile("([0-9a-f]{64})\\.stacktrace");
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final Path root;
    private final Path configFile;
    private final Path inputDirectory;
    private final Path parserPassDirectory;
    private final Path parserFailDirectory;
    private final Path parserCrashDirectory;
    private final Path workDirectory;
    private final Path parserScratchDirectory;
    private final Path lockFile;
    private final List<Path> stageDirectories;
    private final Map<String, Path> parserDestinations;

    private CorpusDirectory(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.configFile = this.root.resolve(CONFIG_FILE_NAME);
        this.inputDirectory = this.root.resolve(INPUT_DIRECTORY_NAME);
        this.parserPassDirectory = this.root.resolve(PARSER_PASS_DIRECTORY_NAME);
        this.parserFailDirectory = this.root.resolve(PARSER_FAIL_DIRECTORY_NAME);
        this.parserCrashDirectory = this.root.resolve(PARSER_CRASH_DIRECTORY_NAME);
        this.workDirectory = this.root.resolve(WORK_DIRECTORY_NAME);
        this.parserScratchDirectory =
                this.workDirectory.resolve(PARSER_SCRATCH_DIRECTORY_NAME);
        this.lockFile = this.root.resolve(LOCK_FILE_NAME);
        this.stageDirectories = List.of(
                inputDirectory,
                parserPassDirectory,
                parserFailDirectory,
                parserCrashDirectory);
        this.parserDestinations = Map.of(
                "pass", parserPassDirectory,
                "fail", parserFailDirectory,
                "crashed", parserCrashDirectory);
    }

    /** Initializes the complete workflow layout without overwriting an existing config. */
    public static CorpusDirectory initialize(Path root) throws IOException, CorpusException {
        Objects.requireNonNull(root, "root");
        var corpus = new CorpusDirectory(root);
        if (Files.exists(corpus.root, NO_FOLLOW_LINKS)
                && !Files.isDirectory(corpus.root, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus path is not a directory: " + root);
        }
        if (Files.exists(corpus.configFile, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration already exists: " + corpus.configFile);
        }

        for (var directory : corpus.allRequiredDirectories()) {
            if (Files.exists(directory, NO_FOLLOW_LINKS)
                    && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow path is not a directory: " + directory);
            }
        }

        Files.createDirectories(corpus.root);
        for (var directory : corpus.allRequiredDirectories()) {
            Files.createDirectories(directory);
        }
        TomlConfig.writeNew(corpus.configFile, FuzzTlaConfig.defaults());
        return corpus;
    }

    /** Opens an initialized corpus and checks every required workflow path. */
    public static CorpusDirectory open(Path root) throws CorpusException {
        Objects.requireNonNull(root, "root");
        var corpus = new CorpusDirectory(root);
        if (!Files.isDirectory(corpus.root, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus directory does not exist: " + root);
        }
        if (!Files.isRegularFile(corpus.configFile, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration file does not exist: " + corpus.configFile);
        }
        for (var directory : corpus.allRequiredDirectories()) {
            if (!Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow directory does not exist: " + directory);
            }
        }
        return corpus;
    }

    /** Reads the strict TOML configuration belonging to this corpus. */
    public FuzzTlaConfig readConfig() throws IOException, ConfigException {
        return TomlConfig.read(configFile);
    }

    /** Acquires the process-wide exclusive lock for this corpus. */
    public CorpusLock acquireLock() throws IOException, CorpusException {
        var channel = FileChannel.open(
                lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            var lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new CorpusException("corpus is already in use: " + root);
            }
            return new CorpusLock(channel, lock);
        } catch (OverlappingFileLockException exception) {
            channel.close();
            throw new CorpusException("corpus is already in use: " + root, exception);
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw exception;
        }
    }

    /**
     * Creates the transient parser scratch area for one workflow invocation.
     *
     * <p>The caller must hold the corpus lock for the lifetime of the returned handle. Any stale
     * parser scratch data from an interrupted invocation is removed before the new run directory
     * is created.
     */
    public synchronized ParserScratch createParserScratch()
            throws IOException, CorpusException {
        if (Files.exists(parserScratchDirectory, NO_FOLLOW_LINKS)
                && !Files.isDirectory(parserScratchDirectory, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    "parser scratch path is not a directory: " + parserScratchDirectory);
        }

        return ParserScratch.create(parserScratchDirectory);
    }

    /**
     * Recovers completed parser transitions, then validates and counts every corpus entry.
     */
    public synchronized CorpusInventory inventory(Generator<?> generator)
            throws IOException, CorpusException {
        Objects.requireNonNull(generator, "generator");
        recoverParserTransitions(generator);

        var names = new HashSet<String>();
        var parserCrashEntries = new HashSet<String>();
        var parserCrashReports = new HashSet<String>();
        var inputs = new ArrayList<Path>();
        long parserPass = 0;
        long parserFail = 0;
        long parserCrash = 0;

        for (var directory : stageDirectories) {
            try (var paths = Files.list(directory)) {
                for (var path : paths.sorted().toList()) {
                    var name = path.getFileName().toString();
                    if (directory.equals(parserCrashDirectory)) {
                        var reportMatcher = PARSER_CRASH_REPORT_FILE_NAME.matcher(name);
                        if (reportMatcher.matches()) {
                            if (!Files.isRegularFile(path, NO_FOLLOW_LINKS)) {
                                throw new CorpusException(
                                        "parser crash report is not a regular file: " + path);
                            }
                            parserCrashReports.add(reportMatcher.group(1));
                            continue;
                        }
                    }

                    var entry = verifyPath(path, generator);
                    if (!names.add(name)) {
                        throw new CorpusException(
                                "corpus entry appears in multiple workflow directories: " + name);
                    }

                    var parserVerdict = stageVerdict(path, entry.encoded());
                    if (directory.equals(inputDirectory)) {
                        if (parserVerdict.isPresent()) {
                            throw new CorpusException(
                                    "completed parser entry remains in input directory: " + path);
                        }
                        inputs.add(path);
                    } else {
                        var expected = expectedVerdict(directory);
                        if (parserVerdict.isEmpty()
                                || !expected.equals(parserVerdict.orElseThrow())) {
                            throw new CorpusException(
                                    "parser verdict does not match workflow directory: " + path);
                        }
                        if (directory.equals(parserPassDirectory)) {
                            parserPass++;
                        } else if (directory.equals(parserFailDirectory)) {
                            parserFail++;
                        } else {
                            parserCrash++;
                            parserCrashEntries.add(name);
                        }
                    }
                }
            }
        }

        for (var digest : parserCrashReports) {
            if (!parserCrashEntries.contains(digest + ".cbor")) {
                throw new CorpusException(
                        "parser crash report has no matching corpus entry: "
                                + parserCrashDirectory.resolve(
                                        digest + PARSER_CRASH_REPORT_EXTENSION));
            }
        }

        return new CorpusInventory(inputs, parserPass, parserFail, parserCrash);
    }

    /** Verifies the complete corpus and returns its total unique-entry count. */
    public long verify(Generator<?> generator) throws IOException, CorpusException {
        return inventory(generator).totalEntries();
    }

    /** Stores an expression input under its payload digest in {@code 00-inputs}. */
    public synchronized StoreResult store(byte[] input) throws IOException, CorpusException {
        var payload = Objects.requireNonNull(input, "input").clone();
        var fileName = hash(payload) + ".cbor";
        for (var directory : stageDirectories) {
            var existingPath = directory.resolve(fileName);
            if (Files.exists(existingPath, NO_FOLLOW_LINKS)) {
                if (Files.isRegularFile(existingPath, NO_FOLLOW_LINKS)) {
                    var existing = decodeExpressionInput(
                            existingPath, Files.readAllBytes(existingPath));
                    if (Arrays.equals(payload, existing)) {
                        return StoreResult.DUPLICATE;
                    }
                }
                throw new CorpusException(
                        "SHA-256 collision at corpus entry: " + existingPath);
            }
        }

        var path = inputDirectory.resolve(fileName);
        var encoded = CorpusInputCodec.encode(CorpusInput.expression(payload));
        try {
            Files.write(path, encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return StoreResult.ADDED;
        } catch (FileAlreadyExistsException exception) {
            throw new CorpusException("corpus entry appeared concurrently: " + path, exception);
        }
    }

    /** Returns the canonical input-stage path for the supplied payload. */
    public Path inputPath(byte[] input) {
        return inputDirectory.resolve(hash(Objects.requireNonNull(input, "input")) + ".cbor");
    }

    /** Reads and validates the required expression payload from one claimed input entry. */
    public synchronized byte[] readExpressionInput(Path path) throws IOException, CorpusException {
        requireInputPath(path);
        return decodeExpressionInput(path, Files.readAllBytes(path));
    }

    /** Records a parser result and moves the input to its result directory. */
    public synchronized Path completeParser(
            Path source, String verdict, Instant startTime, Instant endTime)
            throws IOException, CorpusException {
        return completeParser(source, verdict, startTime, endTime, "");
    }

    /**
     * Records a parser result, preserving a crash diagnostic beside crashed inputs.
     *
     * <p>The diagnostic is ignored for non-crash verdicts. Crash reports are staged before the
     * CBOR metadata commit so an interrupted transition can be completed during inventory.
     */
    public synchronized Path completeParser(
            Path source,
            String verdict,
            Instant startTime,
            Instant endTime,
            String diagnostic)
            throws IOException, CorpusException {
        requireInputPath(source);
        Objects.requireNonNull(diagnostic, "diagnostic");
        var destinationDirectory = parserDestinations.get(verdict);
        if (destinationDirectory == null) {
            throw new IllegalArgumentException("unsupported parser verdict: " + verdict);
        }
        var encoded = Files.readAllBytes(source);
        decodeExpressionInput(source, encoded);
        var destination = destinationDirectory.resolve(source.getFileName());
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            throw new CorpusException("parser destination already exists: " + destination);
        }

        Path stagedCrashReport = null;
        Path crashReportDestination = null;
        if ("crashed".equals(verdict)) {
            var reportName = crashReportName(source);
            stagedCrashReport = workDirectory.resolve(reportName);
            crashReportDestination = parserCrashDirectory.resolve(reportName);
            if (Files.exists(crashReportDestination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "parser crash report already exists: " + crashReportDestination);
            }
        }

        final byte[] updated;
        try {
            updated = CorpusInputCodec.withStageMetadata(
                    encoded,
                    new StageMetadata(PARSER_STAGE_NAME, verdict, startTime, endTime));
        } catch (CorpusInputFormatException exception) {
            throw new CorpusException(
                    "invalid CBOR corpus entry: " + source + ": " + diagnostic(exception),
                    exception);
        }

        var temporary = Files.createTempFile(workDirectory, "parser-", ".cbor");
        var metadataCommitted = false;
        try {
            if (stagedCrashReport != null) {
                stageCrashReport(stagedCrashReport, diagnostic);
            }
            Files.write(
                    temporary,
                    updated,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(
                    temporary,
                    source,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            metadataCommitted = true;
            if (stagedCrashReport != null) {
                Files.move(
                        stagedCrashReport,
                        crashReportDestination,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
            if (!metadataCommitted && stagedCrashReport != null) {
                Files.deleteIfExists(stagedCrashReport);
            }
        }
    }

    public Path root() {
        return root;
    }

    public Path inputDirectory() {
        return inputDirectory;
    }

    public Path parserPassDirectory() {
        return parserPassDirectory;
    }

    public Path parserFailDirectory() {
        return parserFailDirectory;
    }

    public Path parserCrashDirectory() {
        return parserCrashDirectory;
    }

    private List<Path> allRequiredDirectories() {
        var result = new ArrayList<>(stageDirectories);
        result.add(workDirectory);
        return result;
    }

    /** Finishes the second half of an interrupted metadata-then-move transaction. */
    private void recoverParserTransitions(Generator<?> generator)
            throws IOException, CorpusException {
        try (var paths = Files.list(inputDirectory)) {
            for (var path : paths.sorted().toList()) {
                var entry = verifyPath(path, generator);
                var verdict = stageVerdict(path, entry.encoded());
                if (verdict.isPresent()) {
                    var destinationDirectory = parserDestinations.get(verdict.orElseThrow());
                    if (destinationDirectory == null) {
                        throw new CorpusException(
                                "unknown parser verdict in corpus entry: " + path);
                    }
                    var destination = destinationDirectory.resolve(path.getFileName());
                    if (Files.exists(destination, NO_FOLLOW_LINKS)) {
                        throw new CorpusException(
                                "cannot recover duplicate parser entry: " + destination);
                    }
                    if ("crashed".equals(verdict.orElseThrow())) {
                        recoverCrashReport(path);
                    }
                    Files.move(path, destination, StandardCopyOption.ATOMIC_MOVE);
                }
            }
        }
    }

    private void stageCrashReport(Path stagedReport, String diagnostic) throws IOException {
        var report = diagnostic.isBlank()
                ? "Parser crashed without a diagnostic."
                : diagnostic;
        if (!report.endsWith("\n")) {
            report += System.lineSeparator();
        }

        var temporary = Files.createTempFile(workDirectory, "parser-crash-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    report,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(
                    temporary,
                    stagedReport,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void recoverCrashReport(Path source) throws IOException, CorpusException {
        var reportName = crashReportName(source);
        var stagedReport = workDirectory.resolve(reportName);
        var destination = parserCrashDirectory.resolve(reportName);
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "parser crash report is not a regular file: " + destination);
            }
            if (Files.exists(stagedReport, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "parser crash report exists in both work and result directories: "
                                + reportName);
            }
            return;
        }
        if (Files.exists(stagedReport, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(stagedReport, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "staged parser crash report is not a regular file: " + stagedReport);
            }
            Files.move(stagedReport, destination, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static String crashReportName(Path entry) throws CorpusException {
        var matcher = INPUT_FILE_NAME.matcher(entry.getFileName().toString());
        if (!matcher.matches()) {
            throw new CorpusException("invalid corpus entry name: " + entry);
        }
        return matcher.group(1) + PARSER_CRASH_REPORT_EXTENSION;
    }

    private Entry verifyPath(Path path, Generator<?> generator)
            throws IOException, CorpusException {
        if (!Files.isRegularFile(path, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus entry is not a regular file: " + path);
        }
        var matcher = INPUT_FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new CorpusException("invalid corpus entry name: " + path);
        }
        var encoded = Files.readAllBytes(path);
        var input = decodeExpressionInput(path, encoded);
        if (!matcher.group(1).equals(hash(input))) {
            throw new CorpusException("corpus entry hash does not match its input: " + path);
        }
        try {
            generator.generate(input);
        } catch (InputRejectedException exception) {
            throw new CorpusException(
                    "corpus entry is rejected: " + path + ": " + diagnostic(exception), exception);
        }
        return new Entry(encoded);
    }

    private java.util.Optional<String> stageVerdict(Path path, byte[] encoded)
            throws CorpusException {
        try {
            return CorpusInputCodec.stageVerdict(encoded, PARSER_STAGE_NAME);
        } catch (CorpusInputFormatException exception) {
            throw new CorpusException(
                    "invalid parser metadata: " + path + ": " + diagnostic(exception), exception);
        }
    }

    private String expectedVerdict(Path directory) {
        if (directory.equals(parserPassDirectory)) {
            return "pass";
        }
        if (directory.equals(parserFailDirectory)) {
            return "fail";
        }
        if (directory.equals(parserCrashDirectory)) {
            return "crashed";
        }
        throw new IllegalArgumentException("not a parser destination: " + directory);
    }

    private void requireInputPath(Path path) throws CorpusException {
        Objects.requireNonNull(path, "path");
        var normalized = path.toAbsolutePath().normalize();
        if (!inputDirectory.equals(normalized.getParent())) {
            throw new CorpusException("entry is not owned by the input stage: " + path);
        }
        if (!Files.isRegularFile(normalized, NO_FOLLOW_LINKS)) {
            throw new CorpusException("input entry does not exist: " + path);
        }
    }

    private static byte[] decodeExpressionInput(Path path, byte[] encoded) throws CorpusException {
        final CorpusInput corpusInput;
        try {
            corpusInput = CorpusInputCodec.decode(encoded);
        } catch (CorpusInputFormatException exception) {
            throw new CorpusException(
                    "invalid CBOR corpus entry: " + path + ": " + diagnostic(exception),
                    exception);
        }
        if (corpusInput.kind() != CorpusInput.Kind.EXPRESSION) {
            throw new CorpusException(
                    "unsupported corpus input kind '"
                            + corpusInput.kind().encodedName()
                            + "': "
                            + path);
        }
        return corpusInput.input();
    }

    private static String hash(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String diagnostic(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record Entry(byte[] encoded) {}
}
