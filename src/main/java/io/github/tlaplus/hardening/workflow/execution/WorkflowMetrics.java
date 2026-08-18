package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/** Cumulative workflow metrics shared by the runner and its concurrent stages. */
public final class WorkflowMetrics {
    private final long previousTotalElapsedNanos;
    private final ElapsedTimeAccumulator generatorElapsed;
    private final ElapsedTimeAccumulator parserElapsed;
    private final ElapsedTimeAccumulator tlcElapsed;
    private final ElapsedTimeAccumulator apalacheElapsed;
    private final LongAdder attempts = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder richnessRejected = new LongAdder();
    private final LongAdder duplicates = new LongAdder();

    private long generated;
    private long richnessSamples;
    private double minimumRichness;
    private double maximumRichness;
    private double averageRichness;

    public WorkflowMetrics(CorpusRunStatistics previous, long initialEntries) {
        Objects.requireNonNull(previous, "previous");
        if (initialEntries < 0) {
            throw new IllegalArgumentException("initialEntries must be nonnegative");
        }
        previousTotalElapsedNanos = previous.totalElapsedNanos();
        generatorElapsed = elapsed(previous.generatorElapsedNanos());
        parserElapsed = elapsed(previous.parserElapsedNanos());
        tlcElapsed = elapsed(previous.tlcElapsedNanos());
        apalacheElapsed = elapsed(previous.apalacheElapsedNanos());
        attempts.add(previous.generatorAttempts());
        rejected.add(previous.generatorRejected());
        richnessRejected.add(previous.generatorRichnessRejected());
        duplicates.add(previous.generatorDuplicates());
        generated = initialEntries;
        richnessSamples = previous.richnessSamples();
        minimumRichness = previous.minimumRichness();
        maximumRichness = previous.maximumRichness();
        averageRichness = previous.averageRichness();
    }

    public ElapsedTimeAccumulator generatorElapsed() {
        return generatorElapsed;
    }

    public ElapsedTimeAccumulator parserElapsed() {
        return parserElapsed;
    }

    public ElapsedTimeAccumulator tlcElapsed() {
        return tlcElapsed;
    }

    public ElapsedTimeAccumulator apalacheElapsed() {
        return apalacheElapsed;
    }

    public void recordGeneratorAttempt() {
        attempts.increment();
    }

    public void recordGeneratorRejection() {
        rejected.increment();
    }

    public void recordRichnessRejection() {
        richnessRejected.increment();
    }

    public void recordDuplicate() {
        duplicates.increment();
    }

    public synchronized void recordAdmission(double richness) {
        if (!Double.isFinite(richness) || richness < 0.0) {
            throw new IllegalArgumentException("richness must be finite and nonnegative");
        }
        var nextSamples = Math.addExact(richnessSamples, 1);
        if (richnessSamples == 0) {
            minimumRichness = richness;
            maximumRichness = richness;
            averageRichness = richness;
        } else {
            minimumRichness = Math.min(minimumRichness, richness);
            maximumRichness = Math.max(maximumRichness, richness);
            averageRichness += (richness - averageRichness) / nextSamples;
            averageRichness =
                    Math.max(minimumRichness, Math.min(maximumRichness, averageRichness));
        }
        richnessSamples = nextSamples;
        generated = Math.addExact(generated, 1);
    }

    public synchronized PbtStageSummary generatorSummary(long seed) {
        return new PbtStageSummary(
                seed,
                generated,
                attempts.sum(),
                rejected.sum(),
                richnessRejected.sum(),
                duplicates.sum(),
                richnessSamples,
                minimumRichness,
                maximumRichness,
                averageRichness,
                generatorElapsed.elapsed());
    }

    public Duration totalElapsed(Duration invocationElapsed) {
        Objects.requireNonNull(invocationElapsed, "invocationElapsed");
        if (invocationElapsed.isNegative()) {
            throw new IllegalArgumentException("invocationElapsed must be nonnegative");
        }
        return Duration.ofNanos(
                Math.addExact(previousTotalElapsedNanos, invocationElapsed.toNanos()));
    }

    public synchronized CorpusRunStatistics snapshot(Duration invocationElapsed) {
        return new CorpusRunStatistics(
                totalElapsed(invocationElapsed).toNanos(),
                generatorElapsed.elapsed().toNanos(),
                parserElapsed.elapsed().toNanos(),
                tlcElapsed.elapsed().toNanos(),
                apalacheElapsed.elapsed().toNanos(),
                attempts.sum(),
                rejected.sum(),
                richnessRejected.sum(),
                duplicates.sum(),
                richnessSamples,
                minimumRichness,
                maximumRichness,
                averageRichness);
    }

    private static ElapsedTimeAccumulator elapsed(long nanoseconds) {
        return new ElapsedTimeAccumulator(Duration.ofNanos(nanoseconds));
    }
}
