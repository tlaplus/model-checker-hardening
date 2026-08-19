package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One elapsed-time accumulator per corpus stage, seeded from the corpus's durable statistics.
 *
 * <p>A stage's elapsed time is the sum of its workers' active job intervals, so concurrent
 * intervals overlap and a stage duration may exceed the invocation's wall-clock time.
 */
public final class StageClocks {
    private final EnumMap<CorpusStage, ElapsedTimeAccumulator> clocks =
            new EnumMap<>(CorpusStage.class);

    public StageClocks(CorpusRunStatistics previous) {
        Objects.requireNonNull(previous, "previous");
        for (var stage : CorpusStage.values()) {
            clocks.put(
                    stage,
                    new ElapsedTimeAccumulator(
                            Duration.ofNanos(previous.stageElapsedNanos(stage))));
        }
    }

    /** Returns the accumulator that times one stage's active jobs. */
    public ElapsedTimeAccumulator of(CorpusStage stage) {
        return clocks.get(Objects.requireNonNull(stage, "stage"));
    }

    /** Returns the cumulative elapsed time of every stage. */
    public Map<CorpusStage, Long> elapsedNanos() {
        var elapsed = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        clocks.forEach((stage, clock) -> elapsed.put(stage, clock.elapsed().toNanos()));
        return elapsed;
    }
}
