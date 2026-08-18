package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Verdict counters for one stage, seeded from the corpus and updated by its workers. The counters
 * and the stage's elapsed-time accumulator are read together, so one snapshot describes one stage.
 */
public final class StageCounters {
    private final EnumMap<CorpusVerdict, LongAdder> counters =
            new EnumMap<>(CorpusVerdict.class);
    private final ElapsedTimeAccumulator elapsed;

    public StageCounters(StageVerdictSummary initial, ElapsedTimeAccumulator elapsed) {
        Objects.requireNonNull(initial, "initial");
        this.elapsed = Objects.requireNonNull(elapsed, "elapsed");
        for (var verdict : CorpusVerdict.values()) {
            counters.put(verdict, new LongAdder());
        }
        counters.get(CorpusVerdict.PASS).add(initial.passed());
        counters.get(CorpusVerdict.FAIL).add(initial.failed());
        counters.get(CorpusVerdict.CRASH).add(initial.crashed());
    }

    /** Records one processed input. */
    public void record(CorpusVerdict verdict) {
        counters.get(Objects.requireNonNull(verdict, "verdict")).increment();
    }

    public long count(CorpusVerdict verdict) {
        return counters.get(Objects.requireNonNull(verdict, "verdict")).sum();
    }

    /** Returns the stage's cumulative counters and elapsed time. */
    public StageVerdictSummary summary() {
        return new StageVerdictSummary(
                count(CorpusVerdict.PASS),
                count(CorpusVerdict.FAIL),
                count(CorpusVerdict.CRASH),
                elapsed.elapsed());
    }

    /** Returns the accumulator that times this stage's active jobs. */
    public ElapsedTimeAccumulator elapsed() {
        return elapsed;
    }
}
