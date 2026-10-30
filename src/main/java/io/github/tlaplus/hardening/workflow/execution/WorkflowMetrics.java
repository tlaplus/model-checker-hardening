package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Objects;

/**
 * The cumulative statistics of one workflow invocation: how long each stage has been busy, what
 * input generation has produced, and the total elapsed time carried over from earlier runs.
 *
 * <p>This is the in-memory counterpart of the corpus's durable aggregate. {@link
 * #snapshot(Duration)} converts it back into that aggregate on a controlled exit.
 */
public final class WorkflowMetrics {
    private final long previousTotalElapsedNanos;
    private final EnumMap<CorpusStage, ElapsedTimeAccumulator> stageClocks = new EnumMap<>(CorpusStage.class);
    private final GeneratorStatistics generator;

    public WorkflowMetrics(CorpusRunStatistics previous, long initialEntries) {
        Objects.requireNonNull(previous, "previous");
        previousTotalElapsedNanos = previous.totalElapsedNanos();
        for (var stage : CorpusStage.values()) {
            stageClocks.put(stage, new ElapsedTimeAccumulator(Duration.ofNanos(previous.stageElapsedNanos(stage))));
        }
        generator = new GeneratorStatistics(previous, initialEntries);
    }

    /**
     * Returns the accumulator that times one stage's active jobs. A stage's elapsed time is the sum
     * of its workers' active job intervals, so concurrent intervals overlap and a stage duration may
     * exceed the invocation's wall-clock time.
     */
    public ElapsedTimeAccumulator stageClock(CorpusStage stage) {
        return stageClocks.get(Objects.requireNonNull(stage, "stage"));
    }

    /** Returns the shared input-generation counters. */
    public GeneratorStatistics generator() {
        return generator;
    }

    /** Returns this corpus's total elapsed time including the current invocation. */
    public Duration totalElapsed(Duration invocationElapsed) {
        Objects.requireNonNull(invocationElapsed, "invocationElapsed");
        Preconditions.require(!invocationElapsed.isNegative(), "invocationElapsed must be nonnegative");
        return Duration.ofNanos(
                Math.addExact(previousTotalElapsedNanos, invocationElapsed.toNanos()));
    }

    /** Returns the durable aggregate to persist for this invocation. */
    public CorpusRunStatistics snapshot(Duration invocationElapsed) {
        var stageElapsed = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        stageClocks.forEach((stage, clock) -> stageElapsed.put(stage, clock.elapsed().toNanos()));
        return new CorpusRunStatistics(
                totalElapsed(invocationElapsed).toNanos(),
                generator.elapsed().elapsed().toNanos(),
                stageElapsed,
                generator.snapshot());
    }
}
