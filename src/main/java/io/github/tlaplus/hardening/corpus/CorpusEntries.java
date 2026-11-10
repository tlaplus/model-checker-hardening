package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.CRASH_REPORT_FILE_NAME;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.ENTRY_FILE_NAME;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.OrderedParallel;
import io.github.tlaplus.hardening.common.Preconditions;
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
    private final int parallelism;

    /** @param parallelism how many threads {@link #forEachVerified} reads and validates on */
    CorpusEntries(
            CorpusLayout layout, CorpusEntryValidator validator, CorpusEntryStore store, int parallelism) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.store = Objects.requireNonNull(store, "store");
        Preconditions.requirePositive(parallelism, "parallelism");
        this.parallelism = parallelism;
    }

    /** One decoded corpus entry, kept with the bytes it was decoded from. */
    record Entry(Path path, byte[] encoded, CorpusEnvelope envelope) {}

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
                    crashEntries.add(CorpusLayout.entryNameForDigest(reportMatcher.group(1)));
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
        return check(path).entry(store);
    }

    /**
     * Verifies every entry of {@code paths} as {@link #verify} does, reading and validating up to
     * {@code parallelism} entries at a time, and hands them to {@code consumer} in list order on the
     * calling thread. The first entry in list order that fails stops the walk with its failure.
     */
    void forEachVerified(List<Path> paths, EntryVisitor consumer) throws IOException, CorpusException {
        try (var checked = OrderedParallel.map(paths, parallelism, this::check)) {
            while (checked.hasNext()) {
                consumer.visit(checked.next().entry(store));
            }
        }
    }

    /** Receives the verified entries of {@link #forEachVerified}, one at a time. */
    @FunctionalInterface
    interface EntryVisitor {
        void visit(Entry entry) throws IOException, CorpusException;
    }

    /**
     * Reads and validates one entry without side effects, so that it may run on any thread. A
     * failure is returned, not thrown; {@link Checked#entry} raises it on the caller's thread.
     */
    private Checked check(Path path) {
        try {
            var matcher = ENTRY_FILE_NAME.matcher(path.getFileName().toString());
            if (!matcher.matches()) {
                throw new CorpusException("invalid corpus entry name: " + path);
            }
            var encoded = CorpusLayout.readRegularFile(path, "corpus entry");
            var entry = decode(path, encoded);
            var corpusInput = entry.envelope().corpusInput();
            if (!matcher.group(1).equals(CorpusLayout.digest(corpusInput.input()))) {
                throw new CorpusException("corpus entry hash does not match its input: " + path);
            }
            try {
                validator.validate(path, entry.envelope());
            } catch (RuntimeException | StackOverflowError exception) {
                return new Checked.GeneratorCrash(path, corpusInput, exception);
            }
            return new Checked.Valid(entry);
        } catch (IOException exception) {
            return new Checked.Unreadable(exception);
        } catch (CorpusException exception) {
            return new Checked.Invalid(exception);
        }
    }

    /** The outcome of {@link #check}: a valid entry, or the failure to raise for it. */
    private sealed interface Checked {
        /** Returns the entry, or raises its failure; a generator crash is recorded first. */
        Entry entry(CorpusEntryStore store) throws IOException, CorpusException;

        record Valid(Entry valid) implements Checked {
            @Override
            public Entry entry(CorpusEntryStore store) {
                return valid;
            }
        }

        record Unreadable(IOException failure) implements Checked {
            @Override
            public Entry entry(CorpusEntryStore store) throws IOException {
                throw failure;
            }
        }

        record Invalid(CorpusException failure) implements Checked {
            @Override
            public Entry entry(CorpusEntryStore store) throws CorpusException {
                throw failure;
            }
        }

        /**
         * The payload broke the generator: the input is kept for inspection outside the stage
         * directories. The store writes files, so this runs on the caller's thread only.
         */
        record GeneratorCrash(Path path, CorpusInput input, Throwable failure) implements Checked {
            @Override
            public Entry entry(CorpusEntryStore store) throws IOException, CorpusException {
                throw store.generatorCrash(path, input.kind(), input.input(), failure);
            }
        }
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
     * Decodes the kind and generator payload of an entry. Which kinds a run can use is not decided
     * here: the caller that regenerates the payload owns that, and says so through {@link
     * CorpusEntryValidator}.
     */
    static CorpusInput decodeInput(Path path, byte[] encoded) throws CorpusException {
        return decode(path, encoded).envelope().corpusInput();
    }

    /** Reads the kind and payload of an entry owned by one stage's input directory. */
    CorpusInput readOwnedInput(Path path, CorpusStage stage)
            throws IOException, CorpusException {
        requireOwnedPath(path, stage.input(), stage.displayName() + " input");
        return decodeInput(path, CorpusLayout.readRegularFile(path, stage.displayName() + " input entry"));
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

    /** A checker branch must not contain another checker's stage metadata. */
    static void requireMissingOtherCheckerStages(CorpusStage checker, Entry entry)
            throws CorpusException {
        for (var other : CorpusStage.checkerBranches()) {
            if (other != checker) {
                requireMissingStage(entry, other);
            }
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
