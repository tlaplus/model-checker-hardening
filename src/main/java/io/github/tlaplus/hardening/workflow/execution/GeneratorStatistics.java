package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cumulative input-generation counters shared by the generator workers.
 *
 * <p>Attempt counters are contention-free adders. The richness aggregate and the admitted-entry
 * count are updated together under one monitor, so a snapshot always satisfies
 * {@code minimum <= average <= maximum}.
 */
public final class GeneratorStatistics {
    private final ElapsedTimeAccumulator elapsed;
    private final LongAdder attempts = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder richnessRejected = new LongAdder();
    private final LongAdder duplicates = new LongAdder();

    private long generated;
    private long richnessSamples;
    private double minimumRichness;
    private double maximumRichness;
    private double averageRichness;

    public GeneratorStatistics(CorpusRunStatistics previous, long initialEntries) {
        Objects.requireNonNull(previous, "previous");
        if (initialEntries < 0) {
            throw new IllegalArgumentException("initialEntries must be nonnegative");
        }
        elapsed = new ElapsedTimeAccumulator(
                Duration.ofNanos(previous.generatorElapsedNanos()));
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

    /** Returns the accumulator that times active generation. */
    public ElapsedTimeAccumulator elapsed() {
        return elapsed;
    }

    public void recordAttempt() {
        attempts.increment();
    }

    public void recordRejection() {
        rejected.increment();
    }

    public void recordRichnessRejection() {
        richnessRejected.increment();
    }

    public void recordDuplicate() {
        duplicates.increment();
    }

    /** Records one admitted input and folds its richness into the running aggregate. */
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

    /** Returns the cumulative generation counters, tagged with the seed that produced them. */
    public synchronized GeneratorSummary summary(long seed) {
        return new GeneratorSummary(
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
                elapsed.elapsed());
    }

    /** Returns one consistent reading of every generation counter. */
    synchronized Generation generation() {
        return new Generation(
                elapsed.elapsed().toNanos(),
                attempts.sum(),
                rejected.sum(),
                richnessRejected.sum(),
                duplicates.sum(),
                richnessSamples,
                minimumRichness,
                maximumRichness,
                averageRichness);
    }

    /** An atomic reading of the generation counters. */
    record Generation(
            long elapsedNanos,
            long attempts,
            long rejected,
            long richnessRejected,
            long duplicates,
            long richnessSamples,
            double minimumRichness,
            double maximumRichness,
            double averageRichness) {}
}
