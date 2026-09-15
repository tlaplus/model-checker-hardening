package io.github.tlaplus.hardening.checker;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What a model checker explored to reach its verdict, as ADR 0008 defines it.
 *
 * <p>Every part is optional: a checker records what it can measure, and an absent count means that
 * the count was not measured rather than that it is zero.
 *
 * @param phase how far the checker got, when it reports phases
 * @param counts the measured counts
 * @param saturated whether a bounded measurement stopped early, making the state-size counts lower
 *     bounds
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record ExplorationMetrics(
        Optional<ExplorationPhase> phase, Map<ExplorationCount, Long> counts, boolean saturated) {
    public ExplorationMetrics {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(counts, "counts");
        var copy = new EnumMap<ExplorationCount, Long>(ExplorationCount.class);
        counts.forEach((count, value) -> {
            Objects.requireNonNull(count, "count");
            Objects.requireNonNull(value, "value");
            Preconditions.requireNonnegative(value, count.fieldName());
            copy.put(count, value);
        });
        counts = Collections.unmodifiableMap(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public OptionalLong count(ExplorationCount count) {
        var value = counts.get(Objects.requireNonNull(count, "count"));
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /** Collects metrics one at a time. */
    public static final class Builder {
        private ExplorationPhase phase;
        private final EnumMap<ExplorationCount, Long> counts = new EnumMap<>(ExplorationCount.class);
        private boolean saturated;

        private Builder() {}

        public Builder phase(ExplorationPhase value) {
            phase = Objects.requireNonNull(value, "phase");
            return this;
        }

        public Builder count(ExplorationCount count, long value) {
            counts.put(Objects.requireNonNull(count, "count"), value);
            return this;
        }

        public Builder saturated(boolean value) {
            saturated = value;
            return this;
        }

        public ExplorationMetrics build() {
            return new ExplorationMetrics(Optional.ofNullable(phase), counts, saturated);
        }
    }
}
