package io.github.tlaplus.hardening.common;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable generation counters shared by persisted statistics and invocation summaries.
 *
 * <p>{@code knownDefects} counts the candidates that matched a known-defect signature, keyed by
 * primary signature id, whether they were quarantined or discarded past the per-signature cap.
 */
public record GeneratorAggregate(long attempts, long rejected, long richnessRejected,
                                 long duplicates, Map<String, Long> knownDefects,
                                 Richness richness) {
    public GeneratorAggregate {
        Preconditions.requireNonnegative(attempts, "attempts");
        Preconditions.requireNonnegative(rejected, "rejected");
        Preconditions.requireNonnegative(richnessRejected, "richnessRejected");
        Preconditions.requireNonnegative(duplicates, "duplicates");
        Objects.requireNonNull(knownDefects, "knownDefects");
        for (var entry : knownDefects.entrySet()) {
            Preconditions.require(!entry.getKey().isBlank(), "known-defect ids must not be blank");
            Preconditions.requireNonnegative(entry.getValue(), "known-defect count");
        }
        knownDefects = Collections.unmodifiableSortedMap(new TreeMap<>(knownDefects));
        Objects.requireNonNull(richness, "richness");
    }

    /** Returns counters from before any candidate matched a known-defect signature. */
    public GeneratorAggregate(long attempts, long rejected, long richnessRejected,
                              long duplicates, Richness richness) {
        this(attempts, rejected, richnessRejected, duplicates, Map.of(), richness);
    }

    public static GeneratorAggregate empty() {
        return new GeneratorAggregate(0, 0, 0, 0, Richness.empty());
    }

    /** Returns how many candidates matched any known-defect signature. */
    public long knownDefectRejections() {
        return knownDefects.values().stream().mapToLong(Long::longValue).sum();
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
