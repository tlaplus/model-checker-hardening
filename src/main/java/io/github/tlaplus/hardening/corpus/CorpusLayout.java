package io.github.tlaplus.hardening.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
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

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String ENTRY_EXTENSION = ".cbor";
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

    /** Rejects a path that exists but is not a directory. */
    void requireAbsentOrDirectory(List<Path> directories) throws CorpusException {
        for (var directory : directories) {
            if (Files.exists(directory, NO_FOLLOW_LINKS)
                    && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
                throw new CorpusException("workflow path is not a directory: " + directory);
            }
        }
    }

    /** Returns the file name an entry with this payload has in every stage directory. */
    static String entryFileName(byte[] input) {
        return digest(input) + ENTRY_EXTENSION;
    }

    /** Returns the lowercase hexadecimal digest that identifies a payload. */
    static String digest(byte[] input) {
        return HexFormat.of().formatHex(newDigest().digest(input));
    }

    /** Returns the crash-report name beside an entry, rejecting a non-entry name. */
    static String crashReportName(Path entry) throws CorpusException {
        var matcher = ENTRY_FILE_NAME.matcher(entry.getFileName().toString());
        if (!matcher.matches()) {
            throw new CorpusException("invalid corpus entry name: " + entry);
        }
        return matcher.group(1) + CRASH_REPORT_EXTENSION;
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
        return newDigest().getDigestLength() * 2;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is unavailable", exception);
        }
    }
}
