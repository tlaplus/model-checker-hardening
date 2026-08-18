package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.time.Duration;
import java.util.EnumMap;
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
        stages = copyOf(stages, "stages");
        backlog = copyOf(backlog, "backlog");
        Objects.requireNonNull(totalElapsed, "totalElapsed");
        if (corpusEntries < 0) {
            throw new IllegalArgumentException("workflow progress counters must be nonnegative");
        }
        for (var pending : backlog.values()) {
            if (pending < 0) {
                throw new IllegalArgumentException(
                        "workflow progress counters must be nonnegative");
            }
            if (pending > corpusEntries) {
                throw new IllegalArgumentException(
                        "pending stage counts must not exceed corpus entries");
            }
        }
        if (totalElapsed.isNegative()) {
            throw new IllegalArgumentException("total elapsed time must be nonnegative");
        }
    }

    /** Returns what one stage has produced so far. */
    public StageVerdictSummary stage(CorpusStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage"));
    }

    /** Returns how many inputs currently wait for one stage. */
    public long backlog(CorpusStage stage) {
        return backlog.get(Objects.requireNonNull(stage, "stage"));
    }

    private static <T> Map<CorpusStage, T> copyOf(Map<CorpusStage, T> values, String name) {
        Objects.requireNonNull(values, name);
        for (var stage : CorpusStage.values()) {
            if (!values.containsKey(stage)) {
                throw new IllegalArgumentException(name + " is missing stage " + stage);
            }
        }
        var copy = new EnumMap<CorpusStage, T>(CorpusStage.class);
        copy.putAll(values);
        return Map.copyOf(copy);
    }

    /** The externally visible phase of a workflow invocation. */
    public enum Phase {
        RUNNING,
        FINALIZING
    }
}
