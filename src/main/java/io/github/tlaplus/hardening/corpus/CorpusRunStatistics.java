package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.common.Preconditions;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Durable cumulative statistics that cannot be recovered cheaply from corpus entries.
 *
 * <p>Per-stage elapsed time is keyed by {@link CorpusStage}, so a new stage contributes a key
 * rather than a field. Elapsed values are monotonic-clock nanoseconds.
 */
public record CorpusRunStatistics(long totalElapsedNanos, long generatorElapsedNanos,
                                 Map<CorpusStage, Long> stageElapsedNanos, GeneratorAggregate generator) {
    public CorpusRunStatistics {
        Objects.requireNonNull(stageElapsedNanos, "stageElapsedNanos");
        Objects.requireNonNull(generator, "generator");
        var elapsed = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            var nanos = stageElapsedNanos.getOrDefault(stage, 0L);
            Preconditions.requireNonnegative(nanos, "stage elapsed time");
            elapsed.put(stage, nanos);
        }
        stageElapsedNanos = Map.copyOf(elapsed);
        Preconditions.requireNonnegative(totalElapsedNanos, "total elapsed time");
        Preconditions.requireNonnegative(generatorElapsedNanos, "generator elapsed time");
    }

    /** Returns the initial statistics for a corpus that has not completed a tracked run. */
    public static CorpusRunStatistics empty() {
        return new CorpusRunStatistics(0, 0, Map.of(), GeneratorAggregate.empty());
    }

    /** Returns the cumulative elapsed time of one stage's active jobs. */
    public long stageElapsedNanos(CorpusStage stage) {
        return stageElapsedNanos.get(Objects.requireNonNull(stage, "stage"));
    }
}
