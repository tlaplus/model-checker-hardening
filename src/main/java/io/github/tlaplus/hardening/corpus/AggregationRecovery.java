package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.corpus.CorpusEntries.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Recovers aggregate commits and inventories their downstream and upstream verdicts. */
final class AggregationRecovery {
    private final CorpusLayout layout;
    private final CorpusEntries entries;
    private final AggregationTransition transition;

    AggregationRecovery(
            CorpusLayout layout, CorpusEntries entries, AggregationTransition transition) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    /** Finishes source deletion, validates aggregate semantics, and returns durable counts. */
    Results recoverAndValidate() throws IOException, CorpusException {
        var aggregateEntries = new HashMap<String, Entry>();
        var resultCounts = new VerdictTally();
        var residualSources = new ArrayList<Path>();
        var upstreamCounts = new EnumMap<CorpusStage, VerdictTally>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            upstreamCounts.put(checker, new VerdictTally());
        }

        for (var verdict : CorpusStage.AGGREGATOR.resultVerdicts()) {
            for (var path : CorpusLayout.entryPaths(
                    layout.resolve(CorpusStage.AGGREGATOR.result(verdict)))) {
                var entry = entries.verify(path);
                var aggregation = new AggregationInput(
                        entry.path(), transition.upstreamCheckerVerdicts(entry));
                if (verdict != aggregation.conformanceVerdict()) {
                    throw new CorpusException(
                            "aggregator verdict does not match checker verdicts: " + path);
                }
                var name = path.getFileName().toString();
                if (aggregateEntries.put(name, entry) != null) {
                    throw new CorpusException(
                            "aggregator entry appears in multiple result directories: " + name);
                }
                residualSources.addAll(
                        transition.residualSources(entry, verdict, aggregation));
                aggregation.checkerVerdicts().forEach((checker, checkerVerdict) ->
                        upstreamCounts.get(checker).increment(checkerVerdict));
                resultCounts.increment(verdict);
            }
        }
        for (var source : residualSources) {
            Files.delete(source);
        }
        var upstream = new EnumMap<CorpusStage, StageEntryCounts>(CorpusStage.class);
        upstreamCounts.forEach((checker, tally) -> upstream.put(checker, tally.snapshot()));
        return new Results(aggregateEntries, resultCounts.snapshot(), upstream);
    }

    /** Aggregate entries and the verdict history they encode for upstream checker stages. */
    record Results(
            Map<String, Entry> entries,
            StageEntryCounts resultCounts,
            Map<CorpusStage, StageEntryCounts> upstreamCounts) {
        Results {
            entries = Map.copyOf(entries);
            Objects.requireNonNull(resultCounts, "resultCounts");
            upstreamCounts = Map.copyOf(upstreamCounts);
        }
    }
}
