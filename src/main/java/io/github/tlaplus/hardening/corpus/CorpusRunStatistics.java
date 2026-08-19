package io.github.tlaplus.hardening.corpus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Durable cumulative statistics that cannot be recovered cheaply from corpus entries.
 *
 * <p>Per-stage elapsed time is keyed by {@link CorpusStage}, so a new stage contributes a key
 * rather than a field. Elapsed values are monotonic-clock nanoseconds.
 */
public record CorpusRunStatistics(
        long totalElapsedNanos,
        long generatorElapsedNanos,
        Map<CorpusStage, Long> stageElapsedNanos,
        long generatorAttempts,
        long generatorRejected,
        long generatorRichnessRejected,
        long generatorDuplicates,
        long richnessSamples,
        double minimumRichness,
        double maximumRichness,
        double averageRichness) {
    public CorpusRunStatistics {
        Objects.requireNonNull(stageElapsedNanos, "stageElapsedNanos");
        var elapsed = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            elapsed.put(stage, stageElapsedNanos.getOrDefault(stage, 0L));
        }
        stageElapsedNanos = Map.copyOf(elapsed);
        if (totalElapsedNanos < 0
                || generatorElapsedNanos < 0
                || generatorAttempts < 0
                || generatorRejected < 0
                || generatorRichnessRejected < 0
                || generatorDuplicates < 0
                || richnessSamples < 0
                || stageElapsedNanos.values().stream().anyMatch(nanos -> nanos < 0)) {
            throw new IllegalArgumentException("workflow statistics must be nonnegative");
        }
        if (!Double.isFinite(minimumRichness)
                || !Double.isFinite(maximumRichness)
                || !Double.isFinite(averageRichness)
                || minimumRichness < 0.0
                || maximumRichness < 0.0
                || averageRichness < 0.0) {
            throw new IllegalArgumentException(
                    "richness statistics must be finite and nonnegative");
        }
        if (richnessSamples == 0) {
            if (minimumRichness != 0.0 || maximumRichness != 0.0 || averageRichness != 0.0) {
                throw new IllegalArgumentException(
                        "richness statistics must be zero without samples");
            }
        } else if (minimumRichness > averageRichness || averageRichness > maximumRichness) {
            throw new IllegalArgumentException(
                    "average richness must be between the minimum and maximum");
        }
    }

    /** Returns the initial statistics for a corpus that has not completed a tracked run. */
    public static CorpusRunStatistics empty() {
        return new CorpusRunStatistics(0, 0, Map.of(), 0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
    }

    /** Returns the cumulative elapsed time of one stage's active jobs. */
    public long stageElapsedNanos(CorpusStage stage) {
        return stageElapsedNanos.get(Objects.requireNonNull(stage, "stage"));
    }
}
