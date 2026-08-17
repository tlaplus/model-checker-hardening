package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Owns the on-disk layout and integrity checks for one FuzzTLA corpus.
 *
 * <p>A {@code CorpusDirectory} is a path handle, not an open operating-system resource. Use it in
 * one of these flows:
 *
 * <p><strong>Initialization</strong>
 *
 * <ol>
 *   <li>Call {@link #initialize(Path)} once to create the required layout and default
 *       configuration.
 * </ol>
 *
 * <p><strong>Read-only access</strong>
 *
 * <ol>
 *   <li>Call {@link #openExisting(Path)} to check the fixed corpus layout.
 *   <li>Call {@link #readConfig()} and perform the required read-only operation.
 * </ol>
 *
 * <p><strong>Workflow run</strong>
 *
 * <ol>
 *   <li>Call {@link #openExisting(Path)}, then {@link #readConfig()} and construct the
 *       corresponding generator.
 *   <li>Acquire {@link #acquireExclusiveLock()} and hold the returned {@link CorpusLock} for the
 *       remainder of the run.
 *   <li>Create the stage scratch directories while holding the lock.
 *   <li>Call {@link #recoverAndValidate(Generator)} to finish interrupted transitions, validate
 *       every entry, and obtain the initial {@link CorpusInventory}.
 *   <li>Run the stages and keep the lock held for all corpus mutations.
 *   <li>Close the scratch handles, then the corpus lock.
 * </ol>
 *
 * <p>{@code openExisting} performs only the bounded layout check. It neither scans entries nor
 * changes the corpus. Recovery requires a generator derived from the corpus configuration, scans
 * the complete corpus, and may move entries; it therefore belongs inside the locked workflow
 * run.
 */
public final class CorpusDirectory {
    public static final String CRASH_REPORT_EXTENSION = ".stacktrace";

    private static final Pattern INPUT_FILE_NAME = Pattern.compile("([0-9a-f]{64})\\.cbor");
    private static final Pattern CRASH_REPORT_FILE_NAME =
            Pattern.compile("([0-9a-f]{64})\\.stacktrace");
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final EnumMap<CorpusPath, Path> paths;

    private CorpusDirectory(Path root) {
        var normalizedRoot = root.toAbsolutePath().normalize();
        paths = new EnumMap<>(CorpusPath.class);
        for (var corpusPath : CorpusPath.values()) {
            paths.put(corpusPath, normalizedRoot.resolve(corpusPath.relativePath()).normalize());
        }
    }

    /** Initializes the complete workflow layout without overwriting an existing config. */
    public static CorpusDirectory initialize(Path root) throws IOException, CorpusException {
        Objects.requireNonNull(root, "root");
        var corpus = new CorpusDirectory(root);
        var corpusRoot = corpus.resolve(CorpusPath.ROOT);
        var config = corpus.resolve(CorpusPath.CONFIG);
        if (Files.exists(corpusRoot, NO_FOLLOW_LINKS)
                && !Files.isDirectory(corpusRoot, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus path is not a directory: " + root);
        }
        if (Files.exists(config, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration already exists: " + config);
        }
        corpus.validateExistingDirectoryPaths(corpus.requiredDirectories());

        for (var directory : corpus.requiredDirectories()) {
            Files.createDirectories(directory);
        }
        TomlConfig.writeNew(config, FuzzTlaConfig.defaults());
        return corpus;
    }

    /** Opens an initialized corpus and checks every required workflow path. */
    public static CorpusDirectory openExisting(Path root) throws CorpusException {
        Objects.requireNonNull(root, "root");
        var corpus = new CorpusDirectory(root);
        var corpusRoot = corpus.resolve(CorpusPath.ROOT);
        var config = corpus.resolve(CorpusPath.CONFIG);
        if (!Files.isDirectory(corpusRoot, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus directory does not exist: " + root);
        }
        if (!Files.isRegularFile(config, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration file does not exist: " + config);
        }
        for (var directory : corpus.requiredDirectories()) {
            if (!Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow directory does not exist: " + directory);
            }
        }
        return corpus;
    }

    /** Reads the strict TOML configuration belonging to this corpus. */
    public FuzzTlaConfig readConfig() throws IOException, ConfigException {
        return TomlConfig.read(resolve(CorpusPath.CONFIG));
    }

    /** Acquires the process-wide exclusive lock for this corpus. */
    public CorpusLock acquireExclusiveLock() throws IOException, CorpusException {
        var channel = FileChannel.open(
                resolve(CorpusPath.LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            var lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new CorpusException(
                        "corpus is already in use: " + resolve(CorpusPath.ROOT));
            }
            return new CorpusLock(channel, lock);
        } catch (OverlappingFileLockException exception) {
            channel.close();
            throw new CorpusException(
                    "corpus is already in use: " + resolve(CorpusPath.ROOT), exception);
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw exception;
        }
    }

    /** Creates transient parser storage for one locked workflow invocation. */
    public synchronized StageScratch createParserScratch()
            throws IOException, CorpusException {
        return createScratch(CorpusStageLayout.PARSER);
    }

    /** Creates transient TLC storage for one locked workflow invocation. */
    public synchronized StageScratch createTlcScratch()
            throws IOException, CorpusException {
        return createScratch(CorpusStageLayout.TLC);
    }

    /** Recovers durable transitions and returns a validated snapshot of the corpus. */
    public synchronized CorpusInventory recoverAndValidate(Generator<?> generator)
            throws IOException, CorpusException {
        Objects.requireNonNull(generator, "generator");

        // Finish durable transitions before inspecting the steady-state directories.
        recoverTransitions(CorpusStageLayout.PARSER, generator);
        recoverTransitions(CorpusStageLayout.TLC, generator);
        fanOutParserPasses();

        var logicalNames = new HashSet<String>();
        var inputs = new ArrayList<Path>();
        var tlcInputs = new ArrayList<Path>();
        var parserResultCounts = new EnumMap<CorpusVerdict, Long>(CorpusVerdict.class);
        var tlcResultCounts = new EnumMap<CorpusVerdict, Long>(CorpusVerdict.class);

        // Validate inputs that are still waiting for the parser.
        for (var path : entryPaths(resolve(CorpusPath.INPUT))) {
            var entry = verifyPath(path, generator);
            requireMissingStage(
                    entry.path(), entry.encoded(), CorpusStageLayout.PARSER);
            requireMissingStage(entry.path(), entry.encoded(), CorpusStageLayout.TLC);
            addLogicalName(logicalNames, path);
            inputs.add(path);
        }

        // Parser passes must be fanned out; only failures and crashes remain here.
        if (!entryPaths(resolve(CorpusPath.PARSER_PASS)).isEmpty()) {
            throw new CorpusException(
                    "parser pass directory was not drained during checker fan-out");
        }

        for (var verdict : CorpusVerdict.values()) {
            if (verdict == CorpusVerdict.PASS) {
                continue;
            }
            var count = visitResultEntries(
                    CorpusStageLayout.PARSER,
                    verdict,
                    generator,
                    entry -> {
                        requireMissingStage(
                                entry.path(), entry.encoded(), CorpusStageLayout.TLC);
                        addLogicalName(logicalNames, entry.path());
                    });
            parserResultCounts.put(verdict, count);
        }

        // Validate the TLC branch and distinguish pending inputs from completed results.
        var tlcBranch = new HashMap<String, Entry>();
        for (var path : entryPaths(resolve(CorpusPath.TLC_INPUT))) {
            var entry = verifyPath(path, generator);
            requireStageVerdict(entry, CorpusStageLayout.PARSER, CorpusVerdict.PASS);
            requireMissingStage(entry.path(), entry.encoded(), CorpusStageLayout.TLC);
            addTlcBranch(tlcBranch, logicalNames, entry);
            tlcInputs.add(path);
        }
        for (var verdict : CorpusVerdict.values()) {
            var count = visitResultEntries(
                    CorpusStageLayout.TLC,
                    verdict,
                    generator,
                    entry -> {
                        requireStageVerdict(
                                entry, CorpusStageLayout.PARSER, CorpusVerdict.PASS);
                        addTlcBranch(tlcBranch, logicalNames, entry);
                    });
            tlcResultCounts.put(verdict, count);
        }

        // Collect the corresponding parser outputs waiting for Apalache.
        var apalacheBranch = new HashMap<String, Entry>();
        for (var path : entryPaths(resolve(CorpusPath.APALACHE_INPUT))) {
            var entry = verifyPath(path, generator);
            requireStageVerdict(entry, CorpusStageLayout.PARSER, CorpusVerdict.PASS);
            requireMissingStage(entry.path(), entry.encoded(), CorpusStageLayout.TLC);
            var previous = apalacheBranch.put(path.getFileName().toString(), entry);
            if (previous != null) {
                throw new CorpusException("duplicate Apalache input: " + path.getFileName());
            }
        }

        // Require both checker branches to contain identical parser outputs.
        if (!tlcBranch.keySet().equals(apalacheBranch.keySet())) {
            var onlyTlc = new HashSet<>(tlcBranch.keySet());
            onlyTlc.removeAll(apalacheBranch.keySet());
            var onlyApalache = new HashSet<>(apalacheBranch.keySet());
            onlyApalache.removeAll(tlcBranch.keySet());
            throw new CorpusException(
                    "checker branches are inconsistent; only TLC="
                            + onlyTlc
                            + ", only Apalache="
                            + onlyApalache);
        }
        for (var name : tlcBranch.keySet()) {
            requireSameParserOutput(name, tlcBranch.get(name), apalacheBranch.get(name));
        }

        // Publish counts only after the entire corpus has passed validation.
        var parserPass = tlcBranch.size();
        return new CorpusInventory(
                inputs,
                tlcInputs,
                parserPass,
                parserResultCounts.get(CorpusVerdict.FAIL),
                parserResultCounts.get(CorpusVerdict.CRASH),
                tlcResultCounts.get(CorpusVerdict.PASS),
                tlcResultCounts.get(CorpusVerdict.FAIL),
                tlcResultCounts.get(CorpusVerdict.CRASH),
                apalacheBranch.size());
    }

    /** Stores an expression input under its payload digest in {@code 00-inputs}. */
    public synchronized StoreResult store(byte[] input) throws IOException, CorpusException {
        return storeInternal(input, null);
    }

    /** Stores an expression input and its admission-time PBT metadata. */
    public synchronized StoreResult store(byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        return storeInternal(input, Objects.requireNonNull(generation, "generation"));
    }

    private StoreResult storeInternal(byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        var payload = Objects.requireNonNull(input, "input").clone();
        var fileName = hash(payload) + ".cbor";
        for (var corpusPath : CorpusPath.values()) {
            if (!corpusPath.storesEntries()) {
                continue;
            }
            var directory = resolve(corpusPath);
            var existingPath = directory.resolve(fileName);
            if (Files.exists(existingPath, NO_FOLLOW_LINKS)) {
                if (Files.isRegularFile(existingPath, NO_FOLLOW_LINKS)) {
                    var existing = decodeExpressionInput(
                            existingPath, Files.readAllBytes(existingPath));
                    if (Arrays.equals(payload, existing)) {
                        return StoreResult.DUPLICATE;
                    }
                }
                throw new CorpusException("SHA-256 collision at corpus entry: " + existingPath);
            }
        }

        var path = resolve(CorpusPath.INPUT).resolve(fileName);
        var encoded = generation == null
                ? CorpusInputCodec.encode(CorpusInput.expression(payload))
                : CorpusInputCodec.encode(CorpusInput.expression(payload), generation);
        try {
            Files.write(path, encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return StoreResult.ADDED;
        } catch (FileAlreadyExistsException exception) {
            throw new CorpusException("corpus entry appeared concurrently: " + path, exception);
        }
    }

    /** Preserves an input and stack trace for an unexpected generator failure. */
    public synchronized Path recordGeneratorCrash(byte[] input, Throwable failure)
            throws IOException, CorpusException {
        var payload = Objects.requireNonNull(input, "input").clone();
        Objects.requireNonNull(failure, "failure");
        ensureGeneratorCrashDirectory();

        var digest = hash(payload);
        var crashDirectory = resolve(CorpusPath.GENERATOR_CRASH);
        var candidate = crashDirectory.resolve(digest + ".cbor");
        var report = crashDirectory.resolve(digest + CRASH_REPORT_EXTENSION);
        replaceAtomically(candidate, CorpusInputCodec.encode(CorpusInput.expression(payload)));
        try {
            replaceAtomically(report, stackTrace(failure).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new CorpusException(
                    "generator crash candidate was saved to '"
                            + candidate
                            + "', but its stack trace could not be saved",
                    exception);
        }
        return candidate;
    }

    /** Returns the canonical input-stage path for the supplied payload. */
    public Path inputPath(byte[] input) {
        return resolve(CorpusPath.INPUT)
                .resolve(hash(Objects.requireNonNull(input, "input")) + ".cbor");
    }

    /** Reads an entry owned by the parser's input directory. */
    public synchronized byte[] readExpressionInput(Path path) throws IOException, CorpusException {
        return readOwnedExpressionInput(path, CorpusStageLayout.PARSER);
    }

    /** Reads an entry owned by the TLC input directory. */
    public synchronized byte[] readTlcExpressionInput(Path path)
            throws IOException, CorpusException {
        return readOwnedExpressionInput(path, CorpusStageLayout.TLC);
    }

    public synchronized Path completeParser(
            Path source, String verdict, Instant startTime, Instant endTime)
            throws IOException, CorpusException {
        return completeParser(source, verdict, startTime, endTime, "");
    }

    /** Records a parser result and atomically moves it to its result directory. */
    public synchronized Path completeParser(
            Path source,
            String verdict,
            Instant startTime,
            Instant endTime,
            String diagnostic)
            throws IOException, CorpusException {
        return completeStage(
                source,
                CorpusStageLayout.PARSER,
                verdict,
                startTime,
                endTime,
                Optional.empty(),
                diagnostic);
    }

    /** Copies one parser pass into both checker branches, then removes the fan-out source. */
    public synchronized Path fanOutParserPass(Path source) throws IOException, CorpusException {
        requireOwnedPath(source, CorpusPath.PARSER_PASS, "parser pass");
        var encoded = Files.readAllBytes(source);
        var entry = decodeEntry(source, encoded);
        requireStageVerdict(entry, CorpusStageLayout.PARSER, CorpusVerdict.PASS);
        requireMissingStage(entry.path(), entry.encoded(), CorpusStageLayout.TLC);

        var tlcDestination = resolve(CorpusPath.TLC_INPUT).resolve(source.getFileName());
        var apalacheDestination = resolve(CorpusPath.APALACHE_INPUT).resolve(source.getFileName());
        installBranchCopy(source, encoded, tlcDestination);
        installBranchCopy(source, encoded, apalacheDestination);
        Files.delete(source);
        return tlcDestination;
    }

    /** Records a TLC result and atomically moves it to its result directory. */
    public synchronized Path completeTlc(
            Path source,
            String verdict,
            Instant startTime,
            Instant endTime,
            Optional<CheckerFailure> failure,
            String diagnostic)
            throws IOException, CorpusException {
        return completeStage(
                source,
                CorpusStageLayout.TLC,
                verdict,
                startTime,
                endTime,
                failure,
                diagnostic);
    }

    /** Resolves a fixed corpus location. */
    public Path resolve(CorpusPath corpusPath) {
        return paths.get(Objects.requireNonNull(corpusPath, "corpusPath"));
    }

    private StageScratch createScratch(CorpusStageLayout stage)
            throws IOException, CorpusException {
        var directory = resolve(stage.scratch());
        if (Files.exists(directory, NO_FOLLOW_LINKS)
                && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    stage.displayName() + " scratch path is not a directory: " + directory);
        }
        return StageScratch.create(directory);
    }

    private Path completeStage(
            Path source,
            CorpusStageLayout stage,
            String verdict,
            Instant startTime,
            Instant endTime,
            Optional<CheckerFailure> failure,
            String diagnostic)
            throws IOException, CorpusException {
        // Validate the requested transition and resolve its result path before changing the corpus.
        requireOwnedPath(source, stage.input(), stage.displayName() + " input");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(diagnostic, "diagnostic");
        final CorpusVerdict corpusVerdict;
        try {
            corpusVerdict = CorpusVerdict.fromEncodedName(verdict);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "unsupported " + stage.displayName() + " verdict: " + verdict, exception);
        }
        var encoded = Files.readAllBytes(source);
        var entry = decodeEntry(source, encoded);
        requireMissingStage(entry.path(), entry.encoded(), stage);
        var destinationDirectory = resolve(stage.result(corpusVerdict));
        var destination = destinationDirectory.resolve(source.getFileName());
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    stage.displayName() + " destination already exists: " + destination);
        }

        // A crash transition also owns a sidecar that must move with the corpus entry.
        Path stagedCrashReport = null;
        Path crashReportDestination = null;
        if (corpusVerdict == CorpusVerdict.CRASH) {
            var reportName = crashReportName(source);
            stagedCrashReport = stagedCrashReport(stage, reportName);
            crashReportDestination = resolve(stage.result(CorpusVerdict.CRASH)).resolve(reportName);
            if (Files.exists(crashReportDestination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        stage.displayName()
                                + " crash report already exists: "
                                + crashReportDestination);
            }
        }

        // Prepare the updated envelope before committing either artifact.
        final byte[] updated;
        try {
            updated = CorpusInputCodec.withStageMetadata(
                    encoded,
                    new StageMetadata(
                            stage.metadataName(),
                            corpusVerdict.encodedName(),
                            startTime,
                            endTime,
                            failure));
        } catch (CorpusInputFormatException exception) {
            throw new CorpusException(
                    "invalid CBOR corpus entry: " + source + ": " + diagnostic(exception),
                    exception);
        }

        // Commit metadata first; recovery can then finish the sidecar and directory moves.
        var temporary = Files.createTempFile(
                resolve(CorpusPath.WORK), stage.metadataName() + "-", ".cbor");
        var metadataCommitted = false;
        try {
            if (stagedCrashReport != null) {
                stageCrashReport(stagedCrashReport, diagnostic, stage.displayName());
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
            // Preserve a staged sidecar only when committed metadata makes it recoverable.
            Files.deleteIfExists(temporary);
            if (!metadataCommitted && stagedCrashReport != null) {
                Files.deleteIfExists(stagedCrashReport);
            }
        }
    }

    private void recoverTransitions(CorpusStageLayout stage, Generator<?> generator)
            throws IOException, CorpusException {
        for (var path : entryPaths(resolve(stage.input()))) {
            var entry = verifyPath(path, generator);
            var encodedVerdict = stageVerdict(entry.path(), entry.encoded(), stage);
            if (encodedVerdict.isEmpty()) {
                continue;
            }
            final CorpusVerdict verdict;
            try {
                verdict = CorpusVerdict.fromEncodedName(encodedVerdict.orElseThrow());
            } catch (IllegalArgumentException exception) {
                throw new CorpusException(
                        "unknown "
                                + stage.metadataName()
                                + " verdict in corpus entry: "
                                + entry.path(),
                        exception);
            }
            var destination = resolve(stage.result(verdict)).resolve(entry.path().getFileName());
            if (Files.exists(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "cannot recover duplicate "
                                + stage.metadataName()
                                + " entry: "
                                + destination);
            }
            if (verdict == CorpusVerdict.CRASH) {
                recoverCrashReport(entry.path(), stage);
            }
            Files.move(entry.path(), destination, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private void fanOutParserPasses() throws IOException, CorpusException {
        for (var path : entryPaths(resolve(CorpusPath.PARSER_PASS))) {
            fanOutParserPass(path);
        }
    }

    private long visitResultEntries(
            CorpusStageLayout stage,
            CorpusVerdict verdict,
            Generator<?> generator,
            EntryConsumer consumer)
            throws IOException, CorpusException {
        var directory = resolve(stage.result(verdict));
        List<Path> paths;
        var entriesWithReports = new HashSet<String>();
        if (verdict == CorpusVerdict.CRASH) {
            paths = entryPathsAndReports(directory, entriesWithReports, stage.displayName());
        } else {
            paths = entryPaths(directory);
        }

        long count = 0;
        for (var path : paths) {
            var entry = verifyPath(path, generator);
            requireStageVerdict(entry, stage, verdict);
            consumer.accept(entry);
            count++;
        }
        if (verdict == CorpusVerdict.CRASH) {
            validateCrashReports(directory, entriesWithReports, stage.displayName());
        }
        return count;
    }

    private void addTlcBranch(
            Map<String, Entry> branch, Set<String> logicalNames, Entry entry)
            throws CorpusException {
        var name = entry.path().getFileName().toString();
        if (branch.put(name, entry) != null) {
            throw new CorpusException(
                    "TLC entry appears in multiple workflow directories: " + name);
        }
        addLogicalName(logicalNames, entry.path());
    }

    private static void requireSameParserOutput(String name, Entry tlc, Entry apalache)
            throws CorpusException {
        if (!tlc.envelope().corpusInput().equals(apalache.envelope().corpusInput())
                || !tlc.envelope().generation().equals(apalache.envelope().generation())
                || !stageMetadata(tlc.envelope(), CorpusStageLayout.PARSER)
                        .equals(stageMetadata(apalache.envelope(), CorpusStageLayout.PARSER))) {
            throw new CorpusException("checker branch copies disagree for corpus entry: " + name);
        }
    }

    private static Optional<StageMetadata> stageMetadata(
            CorpusEnvelope envelope, CorpusStageLayout stage) {
        return envelope.stages().stream()
                .filter(metadata -> stage.metadataName().equals(metadata.stage()))
                .findFirst();
    }

    private void installBranchCopy(Path source, byte[] encoded, Path destination)
            throws IOException, CorpusException {
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "checker branch destination is not a file: " + destination);
            }
            var existing = decodeEntry(destination, Files.readAllBytes(destination));
            var candidate = decodeEntry(source, encoded);
            requireSameParserOutput(source.getFileName().toString(), existing, candidate);
            return;
        }
        var temporary = Files.createTempFile(resolve(CorpusPath.WORK), "fanout-", ".cbor");
        try {
            Files.write(
                    temporary,
                    encoded,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private List<Path> entryPaths(Path directory) throws IOException, CorpusException {
        var entries = new ArrayList<Path>();
        try (var paths = Files.list(directory)) {
            for (var path : paths.sorted().toList()) {
                if (!INPUT_FILE_NAME.matcher(path.getFileName().toString()).matches()) {
                    throw new CorpusException("invalid corpus entry name: " + path);
                }
                entries.add(path);
            }
        }
        return entries;
    }

    private List<Path> entryPathsAndReports(
            Path directory, Set<String> crashEntries, String displayName)
            throws IOException, CorpusException {
        var entries = new ArrayList<Path>();
        try (var paths = Files.list(directory)) {
            for (var path : paths.sorted().toList()) {
                var name = path.getFileName().toString();
                var reportMatcher = CRASH_REPORT_FILE_NAME.matcher(name);
                if (reportMatcher.matches()) {
                    if (!Files.isRegularFile(path, NO_FOLLOW_LINKS)) {
                        throw new CorpusException(
                                displayName + " crash report is not a regular file: " + path);
                    }
                    crashEntries.add(reportMatcher.group(1) + ".cbor");
                } else if (INPUT_FILE_NAME.matcher(name).matches()) {
                    entries.add(path);
                } else {
                    throw new CorpusException("invalid corpus entry name: " + path);
                }
            }
        }
        return entries;
    }

    private static void validateCrashReports(
            Path directory, Set<String> entriesWithReports, String displayName)
            throws CorpusException {
        for (var name : entriesWithReports) {
            if (!Files.isRegularFile(directory.resolve(name), NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        displayName
                                + " crash report has no matching corpus entry: "
                                + directory.resolve(name.replace(".cbor", ".stacktrace")));
            }
        }
        try (var entries = Files.list(directory)) {
            for (var path : entries.toList()) {
                var matcher = INPUT_FILE_NAME.matcher(path.getFileName().toString());
                if (matcher.matches()
                        && !entriesWithReports.contains(path.getFileName().toString())) {
                    throw new CorpusException(
                            displayName + " crash entry has no stack trace: " + path);
                }
            }
        } catch (IOException exception) {
            throw new CorpusException(
                    "cannot inspect " + displayName + " crash reports", exception);
        }
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
        var entry = decodeEntry(path, encoded);
        var input = entry.envelope().corpusInput().input();
        if (!matcher.group(1).equals(hash(input))) {
            throw new CorpusException("corpus entry hash does not match its input: " + path);
        }
        try {
            generator.generate(input);
        } catch (InputRejectedException exception) {
            throw new CorpusException(
                    "corpus entry is rejected: " + path + ": " + diagnostic(exception), exception);
        } catch (RuntimeException | StackOverflowError exception) {
            throw generatorCrash(path, input, exception);
        }
        return entry;
    }

    private static Entry decodeEntry(Path path, byte[] encoded) throws CorpusException {
        final CorpusEnvelope envelope;
        try {
            envelope = CorpusInputCodec.decodeEnvelope(encoded);
        } catch (CorpusInputFormatException exception) {
            throw new CorpusException(
                    "invalid CBOR corpus entry: " + path + ": " + diagnostic(exception),
                    exception);
        }
        if (envelope.corpusInput().kind() != CorpusInput.Kind.EXPRESSION) {
            throw new CorpusException(
                    "unsupported corpus input kind '"
                            + envelope.corpusInput().kind().encodedName()
                            + "': "
                            + path);
        }
        return new Entry(path, encoded, envelope);
    }

    private byte[] readOwnedExpressionInput(Path path, CorpusStageLayout stage)
            throws IOException, CorpusException {
        requireOwnedPath(path, stage.input(), stage.displayName() + " input");
        return decodeExpressionInput(path, Files.readAllBytes(path));
    }

    private void requireOwnedPath(Path path, CorpusPath owner, String description)
            throws CorpusException {
        Objects.requireNonNull(path, "path");
        var normalized = path.toAbsolutePath().normalize();
        if (!resolve(owner).equals(normalized.getParent())) {
            throw new CorpusException(
                    "entry is not owned by the " + description + " stage: " + path);
        }
        if (!Files.isRegularFile(normalized, NO_FOLLOW_LINKS)) {
            throw new CorpusException(description + " entry does not exist: " + path);
        }
    }

    private void requireStageVerdict(
            Entry entry, CorpusStageLayout stage, CorpusVerdict expected)
            throws CorpusException {
        var actual = stageVerdict(entry.path(), entry.encoded(), stage);
        if (actual.isEmpty() || !expected.encodedName().equals(actual.orElseThrow())) {
            throw new CorpusException(
                    stage.metadataName()
                            + " verdict does not match workflow directory: "
                            + entry.path());
        }
    }

    private void requireMissingStage(Path path, byte[] encoded, CorpusStageLayout stage)
            throws CorpusException {
        if (stageVerdict(path, encoded, stage).isPresent()) {
            throw new CorpusException(
                    "completed "
                            + stage.metadataName()
                            + " entry remains in an input directory: "
                            + path);
        }
    }

    private Optional<String> stageVerdict(
            Path path, byte[] encoded, CorpusStageLayout stage)
            throws CorpusException {
        try {
            return CorpusInputCodec.stageVerdict(encoded, stage.metadataName());
        } catch (CorpusInputFormatException exception) {
            throw new CorpusException(
                    "invalid "
                            + stage.metadataName()
                            + " metadata: "
                            + path
                            + ": "
                            + diagnostic(exception),
                    exception);
        }
    }

    private void addLogicalName(Set<String> names, Path path) throws CorpusException {
        var name = path.getFileName().toString();
        if (!names.add(name)) {
            throw new CorpusException("corpus entry appears in multiple workflow stages: " + name);
        }
    }

    private void stageCrashReport(Path stagedReport, String diagnostic, String displayName)
            throws IOException {
        var report = diagnostic.isBlank()
                ? displayName + " crashed without a diagnostic."
                : diagnostic;
        if (!report.endsWith("\n")) {
            report += System.lineSeparator();
        }
        var temporary = Files.createTempFile(resolve(CorpusPath.WORK), "crash-", ".tmp");
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

    private void recoverCrashReport(Path source, CorpusStageLayout stage)
            throws IOException, CorpusException {
        var reportName = crashReportName(source);
        var stagedReport = stagedCrashReport(stage, reportName);
        var destination = resolve(stage.result(CorpusVerdict.CRASH)).resolve(reportName);
        if (Files.exists(destination, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        stage.metadataName() + " crash report is not a regular file: " + destination);
            }
            if (Files.exists(stagedReport, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        stage.metadataName()
                                + " crash report exists in both work and result directories: "
                                + reportName);
            }
            return;
        }
        if (Files.exists(stagedReport, NO_FOLLOW_LINKS)) {
            if (!Files.isRegularFile(stagedReport, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "staged "
                                + stage.metadataName()
                                + " crash report is not a regular file: "
                                + stagedReport);
            }
            Files.move(stagedReport, destination, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private Path stagedCrashReport(CorpusStageLayout stage, String reportName) {
        return resolve(CorpusPath.WORK).resolve(stage.metadataName() + "-" + reportName);
    }

    private static String crashReportName(Path entry) throws CorpusException {
        var matcher = INPUT_FILE_NAME.matcher(entry.getFileName().toString());
        if (!matcher.matches()) {
            throw new CorpusException("invalid corpus entry name: " + entry);
        }
        return matcher.group(1) + ".stacktrace";
    }

    private CorpusException generatorCrash(Path source, byte[] input, Throwable failure) {
        var message = "cannot generate expression from corpus entry '"
                + source
                + "': "
                + diagnostic(failure);
        try {
            var candidate = recordGeneratorCrash(input, failure);
            message += "; crash saved to '" + candidate + "'";
        } catch (IOException | CorpusException | RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            message += "; crash artifact could not be saved: " + diagnostic(recordingFailure);
        }
        return new CorpusException(message, failure);
    }

    private void ensureGeneratorCrashDirectory() throws IOException, CorpusException {
        var directory = resolve(CorpusPath.GENERATOR_CRASH);
        if (Files.exists(directory, NO_FOLLOW_LINKS)
                && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
            throw new CorpusException(
                    "generator crash path is not a directory: " + directory);
        }
        Files.createDirectories(directory);
    }

    private void validateExistingDirectoryPaths(List<Path> directories) throws CorpusException {
        for (var directory : directories) {
            if (Files.exists(directory, NO_FOLLOW_LINKS)
                    && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow path is not a directory: " + directory);
            }
        }
    }

    private List<Path> requiredDirectories() {
        var result = new ArrayList<Path>();
        for (var corpusPath : CorpusPath.values()) {
            if (corpusPath.isRequired() && corpusPath.isDirectory()) {
                result.add(resolve(corpusPath));
            }
        }
        return result;
    }

    private static void replaceAtomically(Path destination, byte[] contents) throws IOException {
        var temporary = Files.createTempFile(destination.getParent(), "crash-", ".tmp");
        try {
            Files.write(
                    temporary,
                    contents,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] decodeExpressionInput(Path path, byte[] encoded) throws CorpusException {
        return decodeEntry(path, encoded).envelope().corpusInput().input();
    }

    private static String hash(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String stackTrace(Throwable failure) {
        var output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    private static String diagnostic(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    @FunctionalInterface
    private interface EntryConsumer {
        void accept(Entry entry) throws CorpusException;
    }

    private record Entry(Path path, byte[] encoded, CorpusEnvelope envelope) {}
}
