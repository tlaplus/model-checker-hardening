package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.CRASH_REPORT_FILE_NAME;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.ENTRY_FILE_NAME;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads corpus entries and states what makes one valid: a decodable CBOR envelope holding an
 * expression, a name that matches its payload digest, a payload the caller's validator accepts, and
 * stage metadata consistent with the directory the entry sits in.
 *
 * <p>Every failure is reported as a {@link CorpusException} naming the offending path.
 */
final class CorpusEntries {
    private final CorpusLayout layout;
    private final CorpusEntryValidator validator;
    private final CorpusEntryStore store;

    CorpusEntries(CorpusLayout layout, CorpusEntryValidator validator, CorpusEntryStore store) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** One decoded corpus entry, kept with the bytes it was decoded from. */
    record Entry(Path path, byte[] encoded, CorpusEnvelope envelope) {}

    /** Lists the entries of one directory in name order, rejecting any foreign file name. */
    List<Path> entryPaths(Path directory) throws IOException, CorpusException {
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

    /**
     * Lists the entries of a crash directory, collecting the entry names that own a stack-trace
     * sidecar into {@code crashEntries}.
     */
    List<Path> entryPathsAndReports(
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
                } else if (ENTRY_FILE_NAME.matcher(name).matches()) {
                    entries.add(path);
                } else {
                    throw new CorpusException("invalid corpus entry name: " + path);
                }
            }
        }
        return entries;
    }

    /** Reads one entry and checks its name, digest, envelope, and payload. */
    Entry verify(Path path) throws IOException, CorpusException {
        if (!Files.isRegularFile(path, NO_FOLLOW_LINKS)) {
            throw new CorpusException("corpus entry is not a regular file: " + path);
        }
        var matcher = ENTRY_FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new CorpusException("invalid corpus entry name: " + path);
        }
        var encoded = Files.readAllBytes(path);
        var entry = decode(path, encoded);
        var corpusInput = entry.envelope().corpusInput();
        var input = corpusInput.input();
        if (!matcher.group(1).equals(CorpusLayout.digest(input))) {
            throw new CorpusException("corpus entry hash does not match its input: " + path);
        }
        try {
            validator.validate(path, corpusInput);
        } catch (RuntimeException | StackOverflowError exception) {
            // The payload broke the generator: keep it for inspection outside the stage
            // directories.
            throw store.generatorCrash(path, input, exception);
        }
        return entry;
    }

    /**
     * Decodes an envelope. Which input kinds a run consumes is not decided here: an entry the
     * format allows decodes, and the caller that interprets the payload says whether it can use
     * it. See {@link CorpusEntryValidator}.
     */
    static Entry decode(Path path, byte[] encoded) throws CorpusException {
        final CorpusEnvelope envelope;
        try {
            envelope = CorpusEnvelopeCodec.decodeEnvelope(encoded);
        } catch (CorpusFormatException exception) {
            throw new CorpusException(
                    "invalid CBOR corpus entry: " + path + ": " + Diagnostics.message(exception),
                    exception);
        }
        return new Entry(path, encoded, envelope);
    }

    /**
     * Decodes the generator payload of an entry that must hold an expression. Unlike {@link
     * #decode}, this method is asked for an expression specifically, so a different kind is a
     * failed precondition of the call rather than an unusable corpus.
     */
    static byte[] decodeExpressionInput(Path path, byte[] encoded) throws CorpusException {
        var corpusInput = decode(path, encoded).envelope().corpusInput();
        if (corpusInput.kind() != CorpusInput.Kind.EXPRESSION) {
            throw new CorpusException(
                    "corpus entry does not hold an expression input, but '"
                            + corpusInput.kind().encodedName()
                            + "': "
                            + path);
        }
        return corpusInput.input();
    }

    /** Reads the payload of an entry owned by one stage's input directory. */
    byte[] readOwnedExpressionInput(Path path, CorpusStage stage)
            throws IOException, CorpusException {
        requireOwnedPath(path, stage.input(), stage.displayName() + " input");
        return decodeExpressionInput(path, Files.readAllBytes(path));
    }

    /** Rejects a path that does not name an existing entry of the given corpus directory. */
    void requireOwnedPath(Path path, CorpusPath owner, String description)
            throws CorpusException {
        Objects.requireNonNull(path, "path");
        var normalized = path.toAbsolutePath().normalize();
        if (!layout.resolve(owner).equals(normalized.getParent())) {
            throw new CorpusException(
                    "entry is not owned by the " + description + " stage: " + path);
        }
        if (!Files.isRegularFile(normalized, NO_FOLLOW_LINKS)) {
            throw new CorpusException(description + " entry does not exist: " + path);
        }
    }

    /** Requires that a stage recorded exactly the verdict its directory implies. */
    static void requireStageVerdict(Entry entry, CorpusStage stage, CorpusVerdict expected)
            throws CorpusException {
        var actual = entry.envelope().stage(stage).map(StageMetadata::verdict);
        if (actual.isEmpty() || actual.orElseThrow() != expected) {
            throw new CorpusException(
                    stage.metadataName()
                            + " verdict does not match workflow directory: "
                            + entry.path());
        }
    }

    /** Requires that a stage has not yet recorded a verdict for an entry. */
    static void requireMissingStage(Entry entry, CorpusStage stage) throws CorpusException {
        if (entry.envelope().stage(stage).isPresent()) {
            throw new CorpusException(
                    "completed "
                            + stage.metadataName()
                            + " entry remains in an input directory: "
                            + entry.path());
        }
    }

    /**
     * Requires that two checker-branch copies of one logical entry agree on everything the parser
     * produced: the input, its generation metadata, and the parser's own verdict.
     */
    static void requireSameParserOutput(String name, Entry reference, Entry candidate)
            throws CorpusException {
        if (!reference.envelope().corpusInput().equals(candidate.envelope().corpusInput())
                || !reference.envelope().generation().equals(candidate.envelope().generation())
                || !reference.envelope().stage(CorpusStage.PARSER)
                        .equals(candidate.envelope().stage(CorpusStage.PARSER))) {
            throw new CorpusException("checker branch copies disagree for corpus entry: " + name);
        }
    }
}
