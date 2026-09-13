package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.EnumMaps;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Cumulative corpus statistics and stopping reason after one complete workflow invocation. */
public record WorkflowRunSummary(
        StopReason stopReason,
        GeneratorSummary generator,
        Map<CorpusStage, StageVerdictSummary> stages,
        CorpusInventory corpus,
        Duration totalElapsed) {
    public WorkflowRunSummary {
        Objects.requireNonNull(stopReason, "stopReason");
        Objects.requireNonNull(generator, "generator");
        stages = EnumMaps.requireAllKeys(CorpusStage.class, stages, "stages");
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(totalElapsed, "totalElapsed");
        Preconditions.require(!totalElapsed.isNegative(), "total elapsed time must be nonnegative");
    }

    /** Returns what one stage produced over this corpus's lifetime. */
    public StageVerdictSummary stage(CorpusStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage"));
    }

    public enum StopReason {
        COMPLETED,
        CAPACITY_REACHED
    }
}
