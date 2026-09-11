package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.gen.InputKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

/**
 * Admits generated payloads to the input stage and preserves the ones that broke the generator.
 *
 * <p>A payload's digest is its identity, so an entry that already exists anywhere in the corpus
 * is a duplicate rather than a new entry, and a digest collision on differing payloads is an error.
 */
final class CorpusEntryStore {
    private final CorpusLayout layout;

    CorpusEntryStore(CorpusLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** Stores an input of the given kind, with its admission metadata when the caller has some. */
    StoreResult store(InputKind kind, byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        Objects.requireNonNull(kind, "kind");
        var payload = Objects.requireNonNull(input, "input").clone();
        var fileName = CorpusLayout.entryFileName(payload);
        if (isStored(fileName, payload)) {
            return StoreResult.DUPLICATE;
        }

        var path = layout.resolve(CorpusPath.INPUT).resolve(fileName);
        var corpusInput = new CorpusInput(kind, payload);
        var encoded = generation == null
                ? CorpusInputCodec.encode(corpusInput)
                : CorpusInputCodec.encode(corpusInput, generation);
        try {
            Files.write(path, encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return StoreResult.ADDED;
        } catch (FileAlreadyExistsException exception) {
            throw new CorpusException("corpus entry appeared concurrently: " + path, exception);
        }
    }

    /**
     * Stores a candidate that matched known-defect signatures under {@code 00-known-defects}. The
     * entry joins no stage; like an admitted one, it is a duplicate when its payload is anywhere in
     * the corpus.
     */
    StoreResult quarantine(InputKind kind, byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        Objects.requireNonNull(kind, "kind");
        var payload = Objects.requireNonNull(input, "input").clone();
        Objects.requireNonNull(generation, "generation");
        if (generation.knownDefects().isEmpty()) {
            throw new IllegalArgumentException(
                    "a quarantined input must name the known-defect signatures it matched");
        }
        var fileName = CorpusLayout.entryFileName(payload);
        if (isStored(fileName, payload)) {
            return StoreResult.DUPLICATE;
        }
        ensureDirectory(CorpusPath.KNOWN_DEFECTS, "known-defect quarantine");
        layout.createAtomically(
                layout.resolve(CorpusPath.KNOWN_DEFECTS).resolve(fileName),
                "known-defect-",
                CorpusInputCodec.encode(new CorpusInput(kind, payload), generation));
        return StoreResult.ADDED;
    }

    /**
     * Reports whether a payload is already stored under any entry directory, and rejects a digest
     * collision with a different payload.
     */
    private boolean isStored(String fileName, byte[] payload) throws IOException, CorpusException {
        for (var corpusPath : CorpusPath.values()) {
            if (!corpusPath.storesEntries()) {
                continue;
            }
            var existingPath = layout.resolve(corpusPath).resolve(fileName);
            if (Files.exists(existingPath, NO_FOLLOW_LINKS)) {
                if (Files.isRegularFile(existingPath, NO_FOLLOW_LINKS)) {
                    var existing = CorpusEntries.decodeInput(
                            existingPath, Files.readAllBytes(existingPath));
                    if (Arrays.equals(payload, existing.input())) {
                        return true;
                    }
                }
                throw new CorpusException("SHA-256 collision at corpus entry: " + existingPath);
            }
        }
        return false;
    }

    /** Returns the canonical input-stage path for a payload. */
    Path inputPath(byte[] input) {
        return layout.resolve(CorpusPath.INPUT)
                .resolve(CorpusLayout.entryFileName(Objects.requireNonNull(input, "input")));
    }

    /**
     * Preserves an input and its stack trace under {@code .work/generator-crash}. These are
     * diagnostic artifacts: they keep the exact generator bytes without admitting a failing input
     * to a stage directory, and they do not count towards any capacity limit.
     */
    Path recordGeneratorCrash(InputKind kind, byte[] input, Throwable failure)
            throws IOException, CorpusException {
        Objects.requireNonNull(kind, "kind");
        var payload = Objects.requireNonNull(input, "input").clone();
        Objects.requireNonNull(failure, "failure");
        ensureDirectory(CorpusPath.GENERATOR_CRASH, "generator crash");

        var digest = CorpusLayout.digest(payload);
        var crashDirectory = layout.resolve(CorpusPath.GENERATOR_CRASH);
        var candidate = crashDirectory.resolve(CorpusLayout.entryNameForDigest(digest));
        var report = crashDirectory.resolve(CorpusLayout.crashReportNameForDigest(digest));
        layout.replaceAtomically(
                candidate,
                "generator-crash-",
                CorpusInputCodec.encode(new CorpusInput(kind, payload)));
        try {
            layout.replaceAtomically(
                    report,
                    "generator-crash-",
                    Diagnostics.stackTrace(failure).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new CorpusException(
                    "generator crash candidate was saved to '"
                            + candidate
                            + "', but its stack trace could not be saved",
                    exception);
        }
        return candidate;
    }

    /** Tries to preserve a generator failure without replacing the original failure. */
    GeneratorCrashRecording preserveGeneratorCrash(
            InputKind kind, byte[] input, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        try {
            return new GeneratorCrashRecording.Saved(
                    recordGeneratorCrash(kind, input, failure));
        } catch (IOException | CorpusException | RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            return new GeneratorCrashRecording.Failed(recordingFailure);
        }
    }

    /**
     * Describes a generator failure on a stored entry, recording the payload for later inspection.
     */
    CorpusException generatorCrash(
            Path source, InputKind kind, byte[] input, Throwable failure) {
        var message = "cannot generate a specification from corpus entry '"
                + source
                + "': "
                + Diagnostics.message(failure);
        return new CorpusException(
                preserveGeneratorCrash(kind, input, failure).appendTo(message), failure);
    }

    /** Creates a lazily created directory on first use. */
    private void ensureDirectory(CorpusPath corpusPath, String description)
            throws IOException, CorpusException {
        var directory = layout.resolve(corpusPath);
        if (Files.exists(directory, NO_FOLLOW_LINKS)
                && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
            throw new CorpusException(description + " path is not a directory: " + directory);
        }
        Files.createDirectories(directory);
    }
}
