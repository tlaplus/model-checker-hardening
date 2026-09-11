package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import java.time.Duration;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cumulative input-generation counters shared by the generator workers.
 *
 * <p>Attempt counters are contention-free adders. Richness and the admitted-entry count are
 * updated together under one monitor; every immutable snapshot satisfies min <= average <= max.
 * Attempt counters are independent readings, not a transaction across workers.
 */
public final class GeneratorStatistics {
    private final ElapsedTimeAccumulator elapsed;
    private final LongAdder attempts = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder richnessRejected = new LongAdder();
    private final LongAdder duplicates = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> knownDefects = new ConcurrentHashMap<>();
    private long generated;
    private GeneratorAggregate.Richness richness;

    public GeneratorStatistics(CorpusRunStatistics previous, long initialEntries) {
        Objects.requireNonNull(previous, "previous");
        Preconditions.requireNonnegative(initialEntries, "initialEntries");
        elapsed = new ElapsedTimeAccumulator(Duration.ofNanos(previous.generatorElapsedNanos()));
        var aggregate = previous.generator();
        attempts.add(aggregate.attempts());
        rejected.add(aggregate.rejected());
        richnessRejected.add(aggregate.richnessRejected());
        duplicates.add(aggregate.duplicates());
        aggregate.knownDefects().forEach((signature, count) -> knownDefect(signature).add(count));
        generated = initialEntries;
        richness = aggregate.richness();
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

    /** Records a candidate that matched known-defect signatures, under its primary signature. */
    public void recordKnownDefect(String signature) {
        knownDefect(Objects.requireNonNull(signature, "signature")).increment();
    }

    private LongAdder knownDefect(String signature) {
        return knownDefects.computeIfAbsent(signature, _ -> new LongAdder());
    }

    /** Records one admitted input and folds its richness into the running aggregate. */
    public synchronized void recordAdmission(double value) {
        var nextRichness = richness.include(value);
        generated = Math.addExact(generated, 1);
        richness = nextRichness;
    }

    /** Returns cumulative statistics with the current invocation's replay and timing fields. */
    public synchronized GeneratorSummary summary(long seed) {
        return new GeneratorSummary(seed, generated, snapshot(), elapsed.elapsed());
    }

    /** Returns an immutable reading with a consistent richness aggregate. */
    public synchronized GeneratorAggregate snapshot() {
        var knownDefectCounts = new TreeMap<String, Long>();
        knownDefects.forEach((signature, count) -> knownDefectCounts.put(signature, count.sum()));
        return new GeneratorAggregate(attempts.sum(), rejected.sum(), richnessRejected.sum(),
                duplicates.sum(), knownDefectCounts, richness);
    }
}
