package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.CRASH_REPORT_EXTENSION;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    /** Stores an expression input, with its admission metadata when the caller has some. */
    StoreResult store(byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        var payload = Objects.requireNonNull(input, "input").clone();
        var fileName = CorpusLayout.entryFileName(payload);
        for (var corpusPath : CorpusPath.values()) {
            if (!corpusPath.storesEntries()) {
                continue;
            }
            var existingPath = layout.resolve(corpusPath).resolve(fileName);
            if (Files.exists(existingPath, NO_FOLLOW_LINKS)) {
                if (Files.isRegularFile(existingPath, NO_FOLLOW_LINKS)) {
                    var existing = CorpusEntries.decodeExpressionInput(
                            existingPath, Files.readAllBytes(existingPath));
                    if (Arrays.equals(payload, existing)) {
                        return StoreResult.DUPLICATE;
                    }
                }
                throw new CorpusException("SHA-256 collision at corpus entry: " + existingPath);
            }
        }

        var path = layout.resolve(CorpusPath.INPUT).resolve(fileName);
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
    Path recordGeneratorCrash(byte[] input, Throwable failure)
            throws IOException, CorpusException {
        var payload = Objects.requireNonNull(input, "input").clone();
        Objects.requireNonNull(failure, "failure");
        ensureCrashDirectory();

        var digest = CorpusLayout.digest(payload);
        var crashDirectory = layout.resolve(CorpusPath.GENERATOR_CRASH);
        var candidate = crashDirectory.resolve(digest + ".cbor");
        var report = crashDirectory.resolve(digest + CRASH_REPORT_EXTENSION);
        layout.replaceAtomically(
                candidate,
                "generator-crash-",
                CorpusInputCodec.encode(CorpusInput.expression(payload)));
        try {
            layout.replaceAtomically(
                    report,
                    "generator-crash-",
                    stackTrace(failure).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new CorpusException(
                    "generator crash candidate was saved to '"
                            + candidate
                            + "', but its stack trace could not be saved",
                    exception);
        }
        return candidate;
    }

    /**
     * Describes a generator failure on a stored entry, recording the payload for later inspection
     * and reporting whether that succeeded.
     */
    CorpusException generatorCrash(Path source, byte[] input, Throwable failure) {
        var message = "cannot generate expression from corpus entry '"
                + source
                + "': "
                + Diagnostics.message(failure);
        try {
            var candidate = recordGeneratorCrash(input, failure);
            message += "; crash saved to '" + candidate + "'";
        } catch (IOException | CorpusException | RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            message += "; crash artifact could not be saved: "
                    + Diagnostics.message(recordingFailure);
        }
        return new CorpusException(message, failure);
    }

    private void ensureCrashDirectory() throws IOException, CorpusException {
        var directory = layout.resolve(CorpusPath.GENERATOR_CRASH);
        if (Files.exists(directory, NO_FOLLOW_LINKS)
                && !Files.isDirectory(directory, NO_FOLLOW_LINKS)) {
            throw new CorpusException("generator crash path is not a directory: " + directory);
        }
        Files.createDirectories(directory);
    }

    private static String stackTrace(Throwable failure) {
        var output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
