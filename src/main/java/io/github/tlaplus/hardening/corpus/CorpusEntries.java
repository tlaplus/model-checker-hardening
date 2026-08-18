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
        var input = entry.envelope().corpusInput().input();
        if (!matcher.group(1).equals(CorpusLayout.digest(input))) {
            throw new CorpusException("corpus entry hash does not match its input: " + path);
        }
        try {
            validator.validate(path, input);
        } catch (RuntimeException | StackOverflowError exception) {
            // The payload broke the generator: keep it for inspection outside the stage
            // directories.
            throw store.generatorCrash(path, input, exception);
        }
        return entry;
    }

    /** Decodes an envelope, rejecting anything but a supported expression input. */
    static Entry decode(Path path, byte[] encoded) throws CorpusException {
        final CorpusEnvelope envelope;
        try {
            envelope = CorpusInputCodec.decodeEnvelope(encoded);
        } catch (CorpusInputFormatException exception) {
            throw new CorpusException(
                    "invalid CBOR corpus entry: " + path + ": " + Diagnostics.message(exception),
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

    /** Decodes only the generator payload of an entry. */
    static byte[] decodeExpressionInput(Path path, byte[] encoded) throws CorpusException {
        return decode(path, encoded).envelope().corpusInput().input();
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

    /** Returns the encoded verdict a stage recorded for an entry, if it recorded one. */
    static Optional<String> stageVerdict(Path path, byte[] encoded, CorpusStage stage)
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
                            + Diagnostics.message(exception),
                    exception);
        }
    }

    /** Requires that a stage recorded exactly the verdict its directory implies. */
    static void requireStageVerdict(Entry entry, CorpusStage stage, CorpusVerdict expected)
            throws CorpusException {
        var actual = stageVerdict(entry.path(), entry.encoded(), stage);
        if (actual.isEmpty() || !expected.encodedName().equals(actual.orElseThrow())) {
            throw new CorpusException(
                    stage.metadataName()
                            + " verdict does not match workflow directory: "
                            + entry.path());
        }
    }

    /** Requires that a stage has not yet recorded a verdict for an entry. */
    static void requireMissingStage(Path path, byte[] encoded, CorpusStage stage)
            throws CorpusException {
        if (stageVerdict(path, encoded, stage).isPresent()) {
            throw new CorpusException(
                    "completed "
                            + stage.metadataName()
                            + " entry remains in an input directory: "
                            + path);
        }
    }

    /** Returns the metadata one stage recorded in an envelope, if any. */
    static Optional<StageMetadata> stageMetadata(
            CorpusEnvelope envelope, CorpusStage stage) {
        return envelope.stages().stream()
                .filter(metadata -> stage.metadataName().equals(metadata.stage()))
                .findFirst();
    }

    /**
     * Requires that two checker-branch copies of one logical entry agree on everything the parser
     * produced: the input, its generation metadata, and the parser's own verdict.
     */
    static void requireSameParserOutput(String name, Entry reference, Entry candidate)
            throws CorpusException {
        if (!reference.envelope().corpusInput().equals(candidate.envelope().corpusInput())
                || !reference.envelope().generation().equals(candidate.envelope().generation())
                || !stageMetadata(reference.envelope(), CorpusStage.PARSER)
                        .equals(stageMetadata(candidate.envelope(), CorpusStage.PARSER))) {
            throw new CorpusException("checker branch copies disagree for corpus entry: " + name);
        }
    }
}
