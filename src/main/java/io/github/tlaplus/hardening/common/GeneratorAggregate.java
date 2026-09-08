package io.github.tlaplus.hardening.common;

import java.util.Objects;

/** Immutable generation counters shared by persisted statistics and invocation summaries. */
public record GeneratorAggregate(long attempts, long rejected, long richnessRejected,
                                 long duplicates, Richness richness) {
    public GeneratorAggregate {
        Preconditions.requireNonnegative(attempts, "attempts");
        Preconditions.requireNonnegative(rejected, "rejected");
        Preconditions.requireNonnegative(richnessRejected, "richnessRejected");
        Preconditions.requireNonnegative(duplicates, "duplicates");
        Objects.requireNonNull(richness, "richness");
    }

    public static GeneratorAggregate empty() {
        return new GeneratorAggregate(0, 0, 0, 0, Richness.empty());
    }

    /** Collection richness over admitted samples, not over all attempted inputs. */
    public record Richness(long samples, double minimum, double maximum, double average) {
        public Richness {
            Preconditions.requireNonnegative(samples, "samples");
            Preconditions.requireFiniteNonnegative(minimum, "minimum richness");
            Preconditions.requireFiniteNonnegative(maximum, "maximum richness");
            Preconditions.requireFiniteNonnegative(average, "average richness");
            if (samples == 0) {
                Preconditions.require(minimum == 0.0 && maximum == 0.0 && average == 0.0,
                        "richness statistics must be zero without samples");
            } else {
                Preconditions.require(minimum <= average && average <= maximum,
                        "average richness must be between the minimum and maximum");
            }
        }

        public static Richness empty() {
            return new Richness(0, 0.0, 0.0, 0.0);
        }

        /** Adds one sample with a bounded running mean, preserving the snapshot invariants. */
        public Richness include(double value) {
            Preconditions.requireFiniteNonnegative(value, "richness");
            var count = Math.addExact(samples, 1);
            if (samples == 0) {
                return new Richness(count, value, value, value);
            }
            var min = Math.min(minimum, value);
            var max = Math.max(maximum, value);
            var mean = average + (value - average) / count;
            return new Richness(count, min, max, Math.max(min, Math.min(max, mean)));
        }
    }
}
