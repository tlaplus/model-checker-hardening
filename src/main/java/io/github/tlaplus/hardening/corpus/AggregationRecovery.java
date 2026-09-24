package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.corpus.CorpusEntries.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Recovers aggregate commits and inventories the aggregator's result directories. */
final class AggregationRecovery {
    private final CorpusLayout layout;
    private final CorpusEntries entries;
    private final AggregationTransition transition;
    private final AggregationPolicy policy;

    AggregationRecovery(
            CorpusLayout layout,
            CorpusEntries entries,
            AggregationTransition transition,
            AggregationPolicy policy) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.transition = Objects.requireNonNull(transition, "transition");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Finishes source deletion, validates aggregate semantics, and returns durable counts. */
    Results recoverAndValidate() throws IOException, CorpusException {
        var aggregateEntries = new HashMap<String, Entry>();
        var resultCounts = new VerdictTally();
        var residualSources = new ArrayList<Path>();

        for (var verdict : CorpusStage.AGGREGATOR.resultVerdicts()) {
            for (var path : CorpusLayout.entryPaths(
                    layout.resolve(CorpusStage.AGGREGATOR.result(verdict)))) {
                var entry = entries.verify(path);
                var aggregation = new AggregationInput(
                        entry.path(), transition.upstreamCheckerVerdicts(entry), policy.oracle());
                if (verdict != aggregation.verdict()) {
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
                resultCounts.increment(verdict);
            }
        }
        for (var source : residualSources) {
            Files.delete(source);
        }
        return new Results(aggregateEntries, resultCounts.snapshot());
    }

    /**
     * Validates and returns the non-crash checker verdicts an entry that passed through the
     * aggregator carries, wherever it now sits.
     */
    Map<CorpusStage, CorpusVerdict> upstreamCheckerVerdicts(Entry entry) throws CorpusException {
        return transition.upstreamCheckerVerdicts(entry);
    }

    /** The entries of the aggregator's result directories, by name, and their verdict counts. */
    record Results(Map<String, Entry> entries, StageEntryCounts resultCounts) {
        Results {
            entries = Map.copyOf(entries);
            Objects.requireNonNull(resultCounts, "resultCounts");
        }
    }
}
