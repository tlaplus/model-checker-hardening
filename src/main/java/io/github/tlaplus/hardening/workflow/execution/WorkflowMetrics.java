package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import java.time.Duration;
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
    private final StageClocks clocks;
    private final GeneratorStatistics generator;

    public WorkflowMetrics(CorpusRunStatistics previous, long initialEntries) {
        Objects.requireNonNull(previous, "previous");
        previousTotalElapsedNanos = previous.totalElapsedNanos();
        clocks = new StageClocks(previous);
        generator = new GeneratorStatistics(previous, initialEntries);
    }

    /** Returns the per-stage elapsed-time accumulators. */
    public StageClocks clocks() {
        return clocks;
    }

    /** Returns the shared input-generation counters. */
    public GeneratorStatistics generator() {
        return generator;
    }

    /** Returns this corpus's total elapsed time including the current invocation. */
    public Duration totalElapsed(Duration invocationElapsed) {
        Objects.requireNonNull(invocationElapsed, "invocationElapsed");
        if (invocationElapsed.isNegative()) {
            throw new IllegalArgumentException("invocationElapsed must be nonnegative");
        }
        return Duration.ofNanos(
                Math.addExact(previousTotalElapsedNanos, invocationElapsed.toNanos()));
    }

    /** Returns the durable aggregate to persist for this invocation. */
    public CorpusRunStatistics snapshot(Duration invocationElapsed) {
        var generation = generator.generation();
        return new CorpusRunStatistics(
                totalElapsed(invocationElapsed).toNanos(),
                generation.elapsedNanos(),
                clocks.elapsedNanos(),
                generation.attempts(),
                generation.rejected(),
                generation.richnessRejected(),
                generation.duplicates(),
                generation.richnessSamples(),
                generation.minimumRichness(),
                generation.maximumRichness(),
                generation.averageRichness());
    }
}
