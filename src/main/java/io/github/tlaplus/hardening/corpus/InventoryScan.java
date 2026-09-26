package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.ENTRY_FILE_NAME;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.ThrowingConsumer;
import io.github.tlaplus.hardening.corpus.CorpusEntries.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The second pass of startup recovery: validates every entry of a corpus in which no transition is
 * half-finished, and reports what it holds.
 *
 * <p>{@link TransitionRecovery} runs first. This pass lets {@link AggregationRecovery} finish its
 * source deletions, then checks that every entry sits in the directory its stage metadata implies,
 * that the two checker copies of a parser pass agree, and that crash entries and their stack traces
 * pair up.
 *
 * <p>Nothing is published until the whole corpus has passed validation: any inconsistency is
 * reported as a {@link CorpusException} instead of being repaired silently.
 */
final class InventoryScan {
    private final CorpusLayout layout;
    private final CorpusEntries entries;
    private final AggregationRecovery aggregationRecovery;
    private final CheckerSet checkers;

    InventoryScan(
            CorpusLayout layout,
            CorpusEntries entries,
            AggregationRecovery aggregationRecovery,
            CheckerSet checkers) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.aggregationRecovery =
                Objects.requireNonNull(aggregationRecovery, "aggregationRecovery");
        this.checkers = Objects.requireNonNull(checkers, "checkers");
    }

    /** Validates the corpus and returns what each stage holds. */
    CorpusInventory scan() throws IOException, CorpusException {
        var aggregateResults = aggregationRecovery.recoverAndValidate();

        var logicalEntries = new LogicalEntries(checkers);
        var inputs = new ArrayList<Path>();
        var parserResults = new VerdictTally();

        // Validate inputs that are still waiting for the parser.
        for (var path : CorpusLayout.entryPaths(layout.resolve(CorpusPath.INPUT))) {
            var entry = entries.verify(path);
            for (var stage : CorpusStage.values()) {
                CorpusEntries.requireMissingStage(entry, stage);
            }
            logicalEntries.add(entry);
            inputs.add(path);
        }

        // Parser passes must be fanned out; only failures and crashes remain here.
        if (!CorpusLayout.entryPaths(layout.resolve(CorpusPath.PARSER_PASS)).isEmpty()) {
            throw new CorpusException(
                    "parser pass directory was not drained during checker fan-out");
        }

        for (var verdict : CorpusStage.PARSER.resultVerdicts()) {
            if (verdict == CorpusVerdict.PASS) {
                continue;
            }
            var count = visitResultEntries(
                    CorpusStage.PARSER,
                    verdict,
                    entry -> {
                        for (var checker : CorpusStage.checkerBranches()) {
                            CorpusEntries.requireMissingStage(entry, checker);
                        }
                        logicalEntries.add(entry);
                    });
            parserResults.add(verdict, count);
        }

        // Entries past the aggregator carry the checker verdicts they were aggregated from.
        var aggregatedCheckerVerdicts = new EnumMap<CorpusStage, VerdictTally>(CorpusStage.class);
        CorpusStage.checkerBranches().forEach(checker -> aggregatedCheckerVerdicts.put(checker, new VerdictTally()));
        ThrowingConsumer<Entry, CorpusException> aggregated = entry -> {
            aggregationRecovery.upstreamCheckerVerdicts(entry).forEach((checker, verdict) ->
                    aggregatedCheckerVerdicts.get(checker).increment(verdict));
            logicalEntries.add(entry);
        };
        var ungated = new ArrayList<Path>();
        for (var entry : aggregateResults.entries().values()) {
            aggregated.accept(entry);
            if (entry.envelope().stage(CorpusStage.AGGREGATOR).orElseThrow().verdict()
                    == CorpusVerdict.PASS) {
                ungated.add(entry.path());
            }
        }
        ungated.sort(null);
        var gatedResults = new VerdictTally();
        for (var verdict : CorpusStage.QUALITY.resultVerdicts()) {
            gatedResults.add(verdict, visitResultEntries(CorpusStage.QUALITY, verdict, entry -> {
                CorpusEntries.requireStageVerdict(entry, CorpusStage.AGGREGATOR, CorpusVerdict.PASS);
                aggregated.accept(entry);
            }));
        }

        // Validate each checker branch and distinguish pending inputs from completed results.
        var checkerBranches =
                new EnumMap<CorpusStage, CheckerBranch>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            checkerBranches.put(checker, visitCheckerBranch(checker));
        }
        var checkerEntries = validateAndRegisterCheckerBranches(checkerBranches, logicalEntries);
        var gatedEntries = gatedResults.total();
        var parserPass = checkerEntries + aggregateResults.entries().size() + gatedEntries;
        var aggregationCandidates = aggregationCandidates(checkerBranches);

        // Publish counts only after the entire corpus has passed validation.
        var stages = new EnumMap<CorpusStage, CorpusInventory.StageEntries>(CorpusStage.class);
        stages.put(
                CorpusStage.PARSER,
                new CorpusInventory.StageEntries(
                        inputs,
                        StageEntryCounts.from(
                                CorpusStage.PARSER.resultVerdicts(),
                                verdict -> verdict == CorpusVerdict.PASS
                                        ? parserPass
                                        : parserResults.count(verdict)),
                        parserResults.total()));
        for (var checker : CorpusStage.checkerBranches()) {
            var branch = checkerBranches.get(checker);
            var downstream = aggregatedCheckerVerdicts.get(checker);
            stages.put(
                    checker,
                    new CorpusInventory.StageEntries(
                            branch.inputs(),
                            StageEntryCounts.from(
                                    checker.resultVerdicts(),
                                    verdict -> branch.resultCounts().count(verdict)
                                            + downstream.count(verdict)),
                            branch.resultCounts().processed()));
        }
        stages.put(
                CorpusStage.AGGREGATOR,
                new CorpusInventory.StageEntries(
                        aggregationCandidates,
                        StageEntryCounts.from(
                                CorpusStage.AGGREGATOR.resultVerdicts(),
                                verdict -> aggregateResults.resultCounts().count(verdict)
                                        + (verdict == CorpusVerdict.PASS ? gatedEntries : 0)),
                        aggregateResults.entries().size()));
        stages.put(
                CorpusStage.QUALITY,
                new CorpusInventory.StageEntries(
                        ungated,
                        gatedResults.snapshot(),
                        gatedEntries));
        return new CorpusInventory(
                stages, logicalEntries.generations(), logicalEntries.unsettled());
    }

    /** Validates one result directory and reports how many entries it holds. */
    private long visitResultEntries(
            CorpusStage stage,
            CorpusVerdict verdict,
            ThrowingConsumer<Entry, CorpusException> consumer)
            throws IOException, CorpusException {
        var directory = layout.resolve(stage.result(verdict));
        List<Path> paths;
        var entriesWithReports = new HashSet<String>();
        if (verdict == CorpusVerdict.CRASH) {
            paths = entries.entryPathsAndReports(
                    directory, entriesWithReports, stage.displayName());
        } else {
            paths = CorpusLayout.entryPaths(directory);
        }

        long count = 0;
        for (var path : paths) {
            var entry = entries.verify(path);
            CorpusEntries.requireStageVerdict(entry, stage, verdict);
            consumer.accept(entry);
            count++;
        }
        if (verdict == CorpusVerdict.CRASH) {
            validateCrashReports(directory, entriesWithReports, stage.displayName());
        }
        return count;
    }

    /** Validates one checker branch: its pending inputs and each of its result directories. */
    private CheckerBranch visitCheckerBranch(CorpusStage checker)
            throws IOException, CorpusException {
        var inputs = new ArrayList<Path>();
        var branchEntries = new HashMap<String, Entry>();
        var resultEntries = new HashMap<String, Entry>();
        var resultCounts = new VerdictTally();

        for (var path : CorpusLayout.entryPaths(layout.resolve(checker.input()))) {
            var entry = entries.verify(path);
            CorpusEntries.requireStageVerdict(
                    entry, CorpusStage.PARSER, CorpusVerdict.PASS);
            CorpusEntries.requireMissingStage(entry, checker);
            CorpusEntries.requireMissingOtherCheckerStages(checker, entry);
            addCheckerBranchEntry(checker, branchEntries, entry);
            inputs.add(path);
        }
        for (var verdict : checker.resultVerdicts()) {
            var count = visitResultEntries(
                    checker,
                    verdict,
                    entry -> {
                        CorpusEntries.requireStageVerdict(
                                entry, CorpusStage.PARSER, CorpusVerdict.PASS);
                        CorpusEntries.requireMissingOtherCheckerStages(checker, entry);
                        addCheckerBranchEntry(checker, branchEntries, entry);
                        resultEntries.put(entry.path().getFileName().toString(), entry);
                    });
            resultCounts.add(verdict, count);
        }
        return new CheckerBranch(inputs, branchEntries, resultEntries, resultCounts.snapshot());
    }

    /** Returns one notification per checker pair that is already ready to aggregate. */
    private List<Path> aggregationCandidates(Map<CorpusStage, CheckerBranch> branches) {
        var candidates = new ArrayList<Path>();
        for (var entry : branches.get(checkers.first()).results().entrySet()) {
            var name = entry.getKey();
            var ready = true;
            for (var checker : checkers) {
                var candidate = branches.get(checker).results().get(name);
                if (candidate == null
                        || candidate.envelope().stage(checker).orElseThrow().verdict()
                                == CorpusVerdict.CRASH) {
                    ready = false;
                    break;
                }
            }
            if (ready) {
                candidates.add(entry.getValue().path());
            }
        }
        return List.copyOf(candidates);
    }

    private void addCheckerBranchEntry(
            CorpusStage checker, Map<String, Entry> branch, Entry entry)
            throws CorpusException {
        var name = entry.path().getFileName().toString();
        if (branch.put(name, entry) != null) {
            throw new CorpusException(
                    checker.displayName()
                            + " entry appears in multiple workflow directories: "
                            + name);
        }
    }

    /**
     * Requires that every logical entry appears in every branch the corpus runs with identical
     * parser output, and no entry in any other branch, and returns the number of logical entries
     * the parser has passed.
     */
    private long validateAndRegisterCheckerBranches(
            Map<CorpusStage, CheckerBranch> branches, LogicalEntries logicalEntries)
            throws CorpusException {
        for (var checker : CorpusStage.checkerBranches()) {
            if (!checkers.contains(checker) && !branches.get(checker).entries().isEmpty()) {
                throw new CorpusException("corpus holds " + checker.displayName()
                        + " entries, but the run does not use " + checker.displayName());
            }
        }
        var names = new TreeSet<String>();
        for (var checker : checkers) {
            names.addAll(branches.get(checker).entries().keySet());
        }

        var missingDescriptions = new ArrayList<String>();
        for (var checker : checkers) {
            var missing = new TreeSet<>(names);
            missing.removeAll(branches.get(checker).entries().keySet());
            if (!missing.isEmpty()) {
                missingDescriptions.add("from " + checker.displayName() + "=" + missing);
            }
        }
        if (!missingDescriptions.isEmpty()) {
            throw new CorpusException(
                    "checker branches are inconsistent; missing "
                            + String.join(", ", missingDescriptions));
        }

        for (var name : names) {
            Entry reference = null;
            for (var checker : checkers) {
                var candidate = branches.get(checker).entries().get(name);
                if (reference == null) {
                    reference = candidate;
                } else {
                    CorpusEntries.requireSameParserOutput(name, reference, candidate);
                }
            }
            // Each branch copy records only its own checker, so the branches are merged here.
            var branchVerdicts = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
            for (var checker : checkers) {
                branches.get(checker).entries().get(name).envelope().stage(checker)
                        .ifPresent(outcome -> branchVerdicts.put(checker, outcome.verdict()));
            }
            logicalEntries.add(reference, branchVerdicts);
        }
        return names.size();
    }

    /** Requires that crash entries and stack-trace sidecars pair up exactly. */
    private static void validateCrashReports(
            Path directory, Set<String> entriesWithReports, String displayName)
            throws CorpusException {
        for (var name : entriesWithReports) {
            var entry = directory.resolve(name);
            if (!Files.isRegularFile(entry, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        displayName
                                + " crash report has no matching corpus entry: "
                                + directory.resolve(CorpusLayout.crashReportName(entry)));
            }
        }
        try (var paths = Files.list(directory)) {
            for (var path : paths.toList()) {
                var matcher = ENTRY_FILE_NAME.matcher(path.getFileName().toString());
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

    /**
     * The logical entries seen so far: each name once, however many physical copies it has, and
     * how many of them, and of their mutants, each generation admitted.
     */
    private static final class LogicalEntries {
        private final CheckerSet checkers;
        private final Set<EntryName> names = new HashSet<>();
        private final SortedMap<Integer, CorpusInventory.GenerationEntries> generations = new TreeMap<>();
        private final Map<EntryName, EntryProgress> unsettled = new HashMap<>();

        LogicalEntries(CheckerSet checkers) {
            this.checkers = checkers;
        }

        void add(Entry entry) throws CorpusException {
            add(entry, Map.of());
        }

        /**
         * Registers one logical entry. {@code branchVerdicts} carries what the checker branches
         * recorded on their own copies, which no single envelope holds while an entry is still in
         * the pipeline; {@link EntryProgress} then decides what the entry's position means.
         */
        void add(Entry entry, Map<CorpusStage, CorpusVerdict> branchVerdicts) throws CorpusException {
            var name = EntryName.of(entry.path());
            if (!names.add(name)) {
                throw new CorpusException("corpus entry appears in multiple workflow stages: " + name);
            }
            entry.envelope().generation().ifPresent(metadata -> metadata.generation().ifPresent(
                    generation -> {
                        var progress = EntryProgress.of(generation, entry.envelope());
                        for (var branch : branchVerdicts.entrySet()) {
                            progress = progress.with(branch.getKey(), branch.getValue());
                        }
                        generations.merge(
                                generation,
                                new CorpusInventory.GenerationEntries(
                                        1,
                                        metadata.mutation().isPresent() ? 1 : 0,
                                        progress.isUngated() ? 1 : 0),
                                (left, right) -> new CorpusInventory.GenerationEntries(
                                        left.entries() + right.entries(),
                                        left.mutants() + right.mutants(),
                                        left.ungated() + right.ungated()));
                        if (!progress.isSettled(checkers)) {
                            unsettled.put(name, progress);
                        }
                    }));
        }

        SortedMap<Integer, CorpusInventory.GenerationEntries> generations() {
            return generations;
        }

        Map<EntryName, EntryProgress> unsettled() {
            return unsettled;
        }
    }

    /** The pending inputs and completed results of one checker branch. */
    private record CheckerBranch(
            List<Path> inputs,
            Map<String, Entry> entries,
            Map<String, Entry> results,
            StageEntryCounts resultCounts) {
        private CheckerBranch {
            inputs = List.copyOf(inputs);
            entries = Map.copyOf(entries);
            results = Map.copyOf(results);
            Objects.requireNonNull(resultCounts, "resultCounts");
        }
    }
}
