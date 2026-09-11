package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Digests;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where one corpus keeps its files, how an entry is named, and how a file is written into it.
 *
 * <p>Entry and crash-report names are derived from the digest this class computes, so the naming
 * pattern and the digest algorithm cannot drift apart. Every write stages a temporary file under
 * {@link CorpusPath#WORK} and moves it into place, which keeps a partially written file out of any
 * directory a stage or recovery scans.
 *
 * <p>This class is stateless beyond its resolved paths and is not itself synchronized; callers
 * reach it through {@link CorpusDirectory}, which serializes corpus mutations.
 */
final class CorpusLayout {
    static final String CRASH_REPORT_EXTENSION = ".stacktrace";
    static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    static final String ENTRY_EXTENSION = ".cbor";
    private static final String DIGEST_PATTERN = "([0-9a-f]{" + digestHexLength() + "})";

    static final Pattern ENTRY_FILE_NAME =
            Pattern.compile(DIGEST_PATTERN + Pattern.quote(ENTRY_EXTENSION));
    static final Pattern CRASH_REPORT_FILE_NAME =
            Pattern.compile(DIGEST_PATTERN + Pattern.quote(CRASH_REPORT_EXTENSION));

    private final EnumMap<CorpusPath, Path> paths;

    CorpusLayout(Path root) {
        var normalizedRoot = root.toAbsolutePath().normalize();
        paths = new EnumMap<>(CorpusPath.class);
        for (var corpusPath : CorpusPath.values()) {
            paths.put(corpusPath, normalizedRoot.resolve(corpusPath.relativePath()).normalize());
        }
    }

    /** Resolves a fixed corpus location. */
    Path resolve(CorpusPath corpusPath) {
        return paths.get(corpusPath);
    }

    /**
     * Creates every required directory and writes the configuration file, refusing to replace an
     * existing configuration. {@code root} names the corpus in diagnostics as the caller spelled it.
     */
    void initialize(Path root, String configuration) throws IOException, CorpusException {
        var corpusRoot = resolve(CorpusPath.ROOT);
        var config = resolve(CorpusPath.CONFIG);
        if (Files.exists(corpusRoot, NO_FOLLOW_LINKS)
                && !Files.isDirectory(corpusRoot, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus path is not a directory: " + root);
        }
        if (Files.exists(config, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration already exists: " + config);
        }
        var directories = requiredDirectories();
        for (var directory : directories) {
            if (Files.exists(directory, NO_FOLLOW_LINKS)
                    && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow path is not a directory: " + directory);
            }
        }
        for (var directory : directories) {
            Files.createDirectories(directory);
        }
        Files.writeString(
                config,
                configuration,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    /**
     * Checks that the configuration file and every required directory exist, without scanning
     * entries. {@code root} names the corpus in diagnostics as the caller spelled it.
     */
    void requireInitialized(Path root) throws CorpusException {
        if (!Files.isDirectory(resolve(CorpusPath.ROOT), NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus directory does not exist: " + root);
        }
        var config = resolve(CorpusPath.CONFIG);
        if (!Files.isRegularFile(config, NO_FOLLOW_LINKS)) {
            throw new CorpusException("configuration file does not exist: " + config);
        }
        for (var directory : requiredDirectories()) {
            if (!Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow directory does not exist: " + directory);
            }
        }
    }

    /** Returns every directory that a valid corpus must contain. */
    List<Path> requiredDirectories() {
        var result = new ArrayList<Path>();
        for (var corpusPath : CorpusPath.values()) {
            if (corpusPath.isRequired() && corpusPath.isDirectory()) {
                result.add(resolve(corpusPath));
            }
        }
        return result;
    }

    /**
     * Reads a file the corpus creates on first use, or returns empty before then.
     *
     * @param description names the file when the path exists but is not a regular file
     */
    Optional<byte[]> readIfPresent(CorpusPath corpusPath, String description)
            throws IOException, CorpusException {
        var path = resolve(corpusPath);
        if (Files.notExists(path, NO_FOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(path, NO_FOLLOW_LINKS)) {
            throw new CorpusException(description + " is not a regular file: " + path);
        }
        return Optional.of(Files.readAllBytes(path));
    }

    /** Lists the entries of one directory in name order, rejecting any foreign file name. */
    static List<Path> entryPaths(Path directory) throws IOException, CorpusException {
        var entries = new ArrayList<Path>();
        try (var paths = Files.list(directory)) {
            for (var path : paths.sorted().toList()) {
                if (!ENTRY_FILE_NAME.matcher(path.getFileName().toString()).matches()) {
                    throw new CorpusException("invalid corpus entry name: " + path);
                }
                entries.add(path);
            }
        }
        return entries;
    }

    /** Returns the file name an entry with this payload has in every stage directory. */
    static String entryFileName(byte[] input) {
        return entryNameForDigest(digest(input));
    }

    /** Returns the lowercase hexadecimal digest that identifies a payload. */
    static String digest(byte[] input) {
        return Digests.digest(input);
    }

    /** Returns the file name of the entry whose payload has this digest. */
    static String entryNameForDigest(String digest) {
        return digest + ENTRY_EXTENSION;
    }

    /** Returns the file name of the crash report beside the entry whose payload has this digest. */
    static String crashReportNameForDigest(String digest) {
        return digest + CRASH_REPORT_EXTENSION;
    }

    /** Returns the crash-report name beside an entry, rejecting a non-entry name. */
    static String crashReportName(Path entry) throws CorpusException {
        var matcher = ENTRY_FILE_NAME.matcher(entry.getFileName().toString());
        if (!matcher.matches()) {
            throw new CorpusException("invalid corpus entry name: " + entry);
        }
        return crashReportNameForDigest(matcher.group(1));
    }

    /** Returns the work-directory path where a stage stages a crash report before committing it. */
    Path stagedCrashReport(CorpusStage stage, String reportName) {
        return resolve(CorpusPath.WORK).resolve(stage.metadataName() + "-" + reportName);
    }

    /**
     * Writes contents to a temporary file under {@link CorpusPath#WORK}, then atomically moves it
     * onto the destination, replacing any existing file. The temporary file never survives the
     * call. Both paths lie inside the corpus, so the move stays within one file system.
     */
    void replaceAtomically(Path destination, String temporaryPrefix, byte[] contents)
            throws IOException {
        moveAtomically(
                destination,
                temporaryPrefix,
                contents,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** As {@link #replaceAtomically}, but fails when the destination already exists. */
    void createAtomically(Path destination, String temporaryPrefix, byte[] contents)
            throws IOException {
        moveAtomically(destination, temporaryPrefix, contents, StandardCopyOption.ATOMIC_MOVE);
    }

    private void moveAtomically(
            Path destination,
            String temporaryPrefix,
            byte[] contents,
            StandardCopyOption... moveOptions)
            throws IOException {
        var temporary = Files.createTempFile(resolve(CorpusPath.WORK), temporaryPrefix, ".tmp");
        try {
            Files.write(
                    temporary,
                    contents,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(temporary, destination, moveOptions);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int digestHexLength() {
        return Digests.sha256().getDigestLength() * 2;
    }
}
