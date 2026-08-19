package io.github.tlaplus.hardening.workflow.execution;

import java.time.Duration;
import java.util.Objects;

/** Cumulative generation counters, richness statistics, elapsed time, and replay information. */
public record GeneratorSummary(
        long seed,
        long generated,
        long attempts,
        long rejected,
        long richnessRejected,
        long duplicates,
        long richnessSamples,
        double minimumRichness,
        double maximumRichness,
        double averageRichness,
        Duration elapsed) {
    public GeneratorSummary {
        if (seed < 0
                || generated < 0
                || attempts < 0
                || rejected < 0
                || richnessRejected < 0
                || duplicates < 0
                || richnessSamples < 0) {
            throw new IllegalArgumentException("generator counters must be nonnegative");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("generator elapsed time must be nonnegative");
        }
        if (!Double.isFinite(minimumRichness)
                || !Double.isFinite(maximumRichness)
                || !Double.isFinite(averageRichness)
                || minimumRichness < 0.0
                || maximumRichness < 0.0
                || averageRichness < 0.0) {
            throw new IllegalArgumentException(
                    "generator richness statistics must be finite and nonnegative");
        }
        if (richnessSamples == 0) {
            if (minimumRichness != 0.0 || maximumRichness != 0.0 || averageRichness != 0.0) {
                throw new IllegalArgumentException(
                        "generator richness statistics must be zero when there are no samples");
            }
        } else if (minimumRichness > averageRichness || averageRichness > maximumRichness) {
            throw new IllegalArgumentException(
                    "average generator richness must be between the minimum and maximum");
        }
    }
}
