package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/** Stops the invocation clock and saves its aggregate while the corpus lock is still held. */
final class RunStatisticsOnExit implements AutoCloseable {
    private final CorpusDirectory corpus;
    private final WorkflowMetrics metrics;
    private final ElapsedTimeAccumulator invocationElapsed;

    private Duration totalElapsed;

    RunStatisticsOnExit(
            CorpusDirectory corpus,
            WorkflowMetrics metrics,
            ElapsedTimeAccumulator invocationElapsed) {
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.invocationElapsed = Objects.requireNonNull(invocationElapsed, "invocationElapsed");
    }

    @Override
    public void close() throws IOException {
        var interrupted = Thread.interrupted();
        try {
            invocationElapsed.stop();
            var currentInvocation = invocationElapsed.elapsed();
            totalElapsed = metrics.totalElapsed(currentInvocation);
            corpus.writeRunStatistics(metrics.snapshot(currentInvocation));
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Returns the corpus's total elapsed time as saved by {@link #close()}. */
    Duration totalElapsed() {
        if (totalElapsed == null) {
            throw new IllegalStateException("workflow statistics have not been saved");
        }
        return totalElapsed;
    }
}
