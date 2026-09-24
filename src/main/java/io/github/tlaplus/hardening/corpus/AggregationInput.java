package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A logical corpus entry whose non-crash checker results are ready for aggregation. */
public record AggregationInput(
        Path candidate, Map<CorpusStage, CorpusVerdict> checkerVerdicts, Oracle oracle) {
    public AggregationInput {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(checkerVerdicts, "checkerVerdicts");
        Objects.requireNonNull(oracle, "oracle");
        var copy = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        copy.putAll(checkerVerdicts);
        Preconditions.require(copy.keySet().equals(Set.copyOf(CorpusStage.checkerBranches())),
                "checkerVerdicts must name every checker branch exactly once");
        Preconditions.require(!copy.containsValue(CorpusVerdict.CRASH), "crashed checker results cannot be aggregated");
        checkerVerdicts = Map.copyOf(copy);
    }

    /** Returns the aggregator's verdict: the oracle's judgement of the checker verdicts. */
    public CorpusVerdict verdict() {
        return oracle.judge(checkerVerdicts.values());
    }
}
