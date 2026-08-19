package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusLayout.ENTRY_FILE_NAME;
import static io.github.tlaplus.hardening.corpus.CorpusLayout.NO_FOLLOW_LINKS;

import io.github.tlaplus.hardening.common.ThrowingConsumer;
import io.github.tlaplus.hardening.corpus.CorpusEntries.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Brings a corpus to a consistent state at the start of a locked workflow run, then reports what it
 * holds.
 *
 * <p>Recovery runs in two passes. The first finishes the durable transitions an interrupted run
 * left behind: an entry whose stage metadata is committed but which never reached its result
 * directory is moved there, and a parser pass that was not fully fanned out is copied to both
 * checker branches. The second pass validates every remaining entry and counts it, so a returned
 * {@link CorpusInventory} describes a corpus in which no transition is half-finished.
 *
 * <p>Nothing is published until the whole corpus has passed validation: any inconsistency is
 * reported as a {@link CorpusException} instead of being repaired silently.
 */
final class CorpusRecovery {
    private final CorpusLayout layout;
    private final CorpusEntries entries;
    private final StageTransition transitions;

    CorpusRecovery(CorpusLayout layout, CorpusEntries entries, StageTransition transitions) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    /** Recovers durable transitions and returns a validated snapshot of the corpus. */
    CorpusInventory recover() throws IOException, CorpusException {
        // Finish durable transitions before inspecting the steady-state directories.
        for (var stage : CorpusStageLayout.values()) {
            recoverTransitions(stage);
        }
        fanOutParserPasses();

        var logicalNames = new HashSet<String>();
        var inputs = new ArrayList<Path>();
        var parserResultCounts = new EnumMap<CorpusVerdict, Long>(CorpusVerdict.class);

        // Validate inputs that are still waiting for the parser.
        for (var path : entries.entryPaths(layout.resolve(CorpusPath.INPUT))) {
            var entry = entries.verify(path);
            for (var stage : CorpusStageLayout.values()) {
                CorpusEntries.requireMissingStage(entry.path(), entry.encoded(), stage);
            }
            addLogicalName(logicalNames, path.getFileName().toString());
            inputs.add(path);
        }

        // Parser passes must be fanned out; only failures and crashes remain here.
        if (!entries.entryPaths(layout.resolve(CorpusPath.PARSER_PASS)).isEmpty()) {
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
                    entry -> {
                        for (var checker : CorpusStageLayout.checkerBranches()) {
                            CorpusEntries.requireMissingStage(
                                    entry.path(), entry.encoded(), checker);
                        }
                        addLogicalName(logicalNames, entry.path().getFileName().toString());
                    });
            parserResultCounts.put(verdict, count);
        }

        // Validate each checker branch and distinguish pending inputs from completed results.
        var checkerBranches =
                new EnumMap<CorpusStageLayout, CheckerBranch>(CorpusStageLayout.class);
        for (var checker : CorpusStageLayout.checkerBranches()) {
            checkerBranches.put(checker, visitCheckerBranch(checker));
        }
        var parserPass = validateAndRegisterCheckerBranches(checkerBranches, logicalNames);
        var tlcBranch = checkerBranches.get(CorpusStageLayout.TLC);
        var apalacheBranch = checkerBranches.get(CorpusStageLayout.APALACHE);

        // Publish counts only after the entire corpus has passed validation.
        return new CorpusInventory(
                inputs,
                tlcBranch.inputs(),
                apalacheBranch.inputs(),
                parserPass,
                parserResultCounts.get(CorpusVerdict.FAIL),
                parserResultCounts.get(CorpusVerdict.CRASH),
                tlcBranch.resultCount(CorpusVerdict.PASS),
                tlcBranch.resultCount(CorpusVerdict.FAIL),
                tlcBranch.resultCount(CorpusVerdict.CRASH),
                apalacheBranch.resultCount(CorpusVerdict.PASS),
                apalacheBranch.resultCount(CorpusVerdict.FAIL),
                apalacheBranch.resultCount(CorpusVerdict.CRASH));
    }

    /**
     * Moves entries whose stage metadata was committed before an interruption into the result
     * directory their verdict names, completing the crash sidecar on the way.
     */
    private void recoverTransitions(CorpusStageLayout stage) throws IOException, CorpusException {
        for (var path : entries.entryPaths(layout.resolve(stage.input()))) {
            var entry = entries.verify(path);
            var encodedVerdict = CorpusEntries.stageVerdict(entry.path(), entry.encoded(), stage);
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
            var destination =
                    layout.resolve(stage.result(verdict)).resolve(entry.path().getFileName());
            if (Files.exists(destination, NO_FOLLOW_LINKS)) {
                throw new CorpusException(
                        "cannot recover duplicate "
                                + stage.metadataName()
                                + " entry: "
                                + destination);
            }
            if (verdict == CorpusVerdict.CRASH) {
                transitions.recoverCrashReport(entry.path(), stage);
            }
            Files.move(entry.path(), destination, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private void fanOutParserPasses() throws IOException, CorpusException {
        for (var path : entries.entryPaths(layout.resolve(CorpusPath.PARSER_PASS))) {
            transitions.fanOutParserPass(path);
        }
    }

    /** Validates one result directory and reports how many entries it holds. */
    private long visitResultEntries(
            CorpusStageLayout stage,
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
            paths = entries.entryPaths(directory);
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
    private CheckerBranch visitCheckerBranch(CorpusStageLayout checker)
            throws IOException, CorpusException {
        var inputs = new ArrayList<Path>();
        var branchEntries = new HashMap<String, Entry>();
        var resultCounts = new EnumMap<CorpusVerdict, Long>(CorpusVerdict.class);

        for (var path : entries.entryPaths(layout.resolve(checker.input()))) {
            var entry = entries.verify(path);
            CorpusEntries.requireStageVerdict(
                    entry, CorpusStageLayout.PARSER, CorpusVerdict.PASS);
            CorpusEntries.requireMissingStage(entry.path(), entry.encoded(), checker);
            requireMissingOtherCheckerStages(checker, entry);
            addCheckerBranchEntry(checker, branchEntries, entry);
            inputs.add(path);
        }
        for (var verdict : CorpusVerdict.values()) {
            var count = visitResultEntries(
                    checker,
                    verdict,
                    entry -> {
                        CorpusEntries.requireStageVerdict(
                                entry, CorpusStageLayout.PARSER, CorpusVerdict.PASS);
                        requireMissingOtherCheckerStages(checker, entry);
                        addCheckerBranchEntry(checker, branchEntries, entry);
                    });
            resultCounts.put(verdict, count);
        }
        return new CheckerBranch(inputs, branchEntries, resultCounts);
    }

    private void addCheckerBranchEntry(
            CorpusStageLayout checker, Map<String, Entry> branch, Entry entry)
            throws CorpusException {
        var name = entry.path().getFileName().toString();
        if (branch.put(name, entry) != null) {
            throw new CorpusException(
                    checker.displayName()
                            + " entry appears in multiple workflow directories: "
                            + name);
        }
    }

    private void requireMissingOtherCheckerStages(CorpusStageLayout checker, Entry entry)
            throws CorpusException {
        for (var otherChecker : CorpusStageLayout.checkerBranches()) {
            if (otherChecker != checker) {
                CorpusEntries.requireMissingStage(
                        entry.path(), entry.encoded(), otherChecker);
            }
        }
    }

    /**
     * Requires that every logical entry appears in every checker branch with identical parser
     * output, and returns the number of logical entries the parser has passed.
     */
    private long validateAndRegisterCheckerBranches(
            Map<CorpusStageLayout, CheckerBranch> branches, Set<String> logicalNames)
            throws CorpusException {
        var logicalEntries = new TreeSet<String>();
        for (var checker : CorpusStageLayout.checkerBranches()) {
            logicalEntries.addAll(branches.get(checker).entries().keySet());
        }

        var missingDescriptions = new ArrayList<String>();
        for (var checker : CorpusStageLayout.checkerBranches()) {
            var missing = new TreeSet<>(logicalEntries);
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

        for (var name : logicalEntries) {
            Entry reference = null;
            for (var checker : CorpusStageLayout.checkerBranches()) {
                var candidate = branches.get(checker).entries().get(name);
                if (reference == null) {
                    reference = candidate;
                } else {
                    CorpusEntries.requireSameParserOutput(name, reference, candidate);
                }
            }
            addLogicalName(logicalNames, name);
        }
        return logicalEntries.size();
    }

    /** Requires that crash entries and stack-trace sidecars pair up exactly. */
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

    private static void addLogicalName(Set<String> names, String name) throws CorpusException {
        if (!names.add(name)) {
            throw new CorpusException("corpus entry appears in multiple workflow stages: " + name);
        }
    }

    /** The pending inputs and completed results of one checker branch. */
    private record CheckerBranch(
            List<Path> inputs,
            Map<String, Entry> entries,
            EnumMap<CorpusVerdict, Long> resultCounts) {
        private CheckerBranch {
            inputs = List.copyOf(inputs);
            entries = Map.copyOf(entries);
            resultCounts = new EnumMap<>(resultCounts);
        }

        long resultCount(CorpusVerdict verdict) {
            return resultCounts.get(verdict);
        }
    }
}
