package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A logical corpus entry whose non-crash checker results are ready for aggregation. The oracle
 * judges the whole records: verdicts and, for completed searches, state counts (ADR 0019).
 */
public record AggregationInput(
        Path candidate, Map<CorpusStage, StageRecord> checkerResults, CheckingPolicy policy) {
    public AggregationInput {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(checkerResults, "checkerResults");
        Objects.requireNonNull(policy, "policy");
        var copy = new EnumMap<CorpusStage, StageRecord>(CorpusStage.class);
        copy.putAll(checkerResults);
        Preconditions.require(copy.keySet().equals(Set.copyOf(policy.checkers().stages())),
                "checkerResults must name every checker the corpus runs exactly once");
        Preconditions.require(copy.values().stream().noneMatch(result -> result.verdict() == CorpusVerdict.CRASH),
                "crashed checker results cannot be aggregated");
        checkerResults = Map.copyOf(copy);
    }

    /** Returns the verdict of each checker. */
    public Map<CorpusStage, CorpusVerdict> checkerVerdicts() {
        var verdicts = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        checkerResults.forEach((checker, result) -> verdicts.put(checker, result.verdict()));
        return Map.copyOf(verdicts);
    }

    /** Returns the aggregator's verdict: the oracle's judgement of the checker results. */
    public CorpusVerdict verdict() {
        return policy.oracle().judge(checkerResults.values());
    }
}
