package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.EnumMaps;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Best-effort snapshot of cumulative corpus statistics and current pending work.
 *
 * <p>Per-stage figures are keyed by {@link CorpusStage}: {@code stages} holds what each stage has
 * produced, and {@code backlog} how many inputs currently wait for it.
 */
public record WorkflowProgress(
        Phase phase,
        GeneratorSummary generator,
        Map<CorpusStage, StageVerdictSummary> stages,
        Map<CorpusStage, Long> backlog,
        long corpusEntries,
        Duration totalElapsed) {
    public WorkflowProgress {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(generator, "generator");
        stages = EnumMaps.requireAllKeys(CorpusStage.class, stages, "stages");
        backlog = EnumMaps.requireAllKeys(CorpusStage.class, backlog, "backlog");
        Objects.requireNonNull(totalElapsed, "totalElapsed");
        Preconditions.require(corpusEntries >= 0, "workflow progress counters must be nonnegative");
        for (var pending : backlog.values()) {
            Preconditions.require(pending >= 0, "workflow progress counters must be nonnegative");
            Preconditions.require(pending <= corpusEntries, "pending stage counts must not exceed corpus entries");
        }
        Preconditions.require(!totalElapsed.isNegative(), "total elapsed time must be nonnegative");
    }

    /** Returns what one stage has produced so far. */
    public StageVerdictSummary stage(CorpusStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage"));
    }

    /** Returns how many inputs currently wait for one stage. */
    public long backlog(CorpusStage stage) {
        return backlog.get(Objects.requireNonNull(stage, "stage"));
    }

    /** The externally visible phase of a workflow invocation. */
    public enum Phase {
        RUNNING,
        FINALIZING
    }
}
