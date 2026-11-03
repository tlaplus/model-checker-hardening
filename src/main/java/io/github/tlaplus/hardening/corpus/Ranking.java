package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import java.util.List;
import java.util.Objects;
import java.util.function.LongUnaryOperator;

/**
 * How the quality gate ranks the admissible entries of a generation, best first: the components of
 * its key, compared lexicographically, higher first. The key's components also make up the
 * behaviour cell of an entry (ADR 0013). Each {@link Technique} names its ranking.
 */
@ArchRole(Role.POLICY)
public enum Ranking {
    /**
     * Depth of state change first (ADR 0010), then the data valuations, then the temporal shapes of
     * the property (ADR 0028 §3).
     */
    DEPTH(List.of(
            new Component(ExplorationCount.PROJECTED_DEPTH, Scale.LINEAR),
            new Component(ExplorationCount.PROJECTED_STATES, Scale.LOG2),
            new Component(Measure.Replayed.TEMPORAL_SHAPES, Scale.LOG2),
            new Component(ExplorationCount.ACTIONS_DISCOVERING, Scale.LINEAR),
            new Component(ExplorationCount.MAX_STATE_NODES, Scale.LOG2),
            new Component(ExplorationCount.MAX_CARDINALITY, Scale.LOG2),
            new Component(ExplorationCount.MAX_NESTING, Scale.LINEAR))),
    /**
     * Actions that produce new data first, then the data valuations, then the depth of data change
     * (ADR 0024 §2). {@code dataActions} is bounded only by the edges of a graph, so it is
     * bucketed: one more data action must not outrank an order of magnitude more valuations.
     */
    ACTION_COVERAGE(List.of(
            new Component(ExplorationCount.DATA_ACTIONS, Scale.LOG2),
            new Component(ExplorationCount.PROJECTED_STATES, Scale.LOG2),
            new Component(ExplorationCount.PROJECTED_DEPTH, Scale.LINEAR),
            new Component(ExplorationCount.MAX_STATE_NODES, Scale.LOG2),
            new Component(ExplorationCount.MAX_CARDINALITY, Scale.LOG2),
            new Component(ExplorationCount.MAX_NESTING, Scale.LINEAR)));

    /** How a count is compared. */
    public enum Scale {
        LINEAR(value -> value),
        /** {@code floor(log2(value + 1))}: the number of binary digits of the value. */
        LOG2(value -> Long.SIZE - 1 - Long.numberOfLeadingZeros(value + 1));

        private final LongUnaryOperator bucket;

        Scale(LongUnaryOperator bucket) {
            this.bucket = bucket;
        }

        public long bucket(long value) {
            return bucket.applyAsLong(value);
        }
    }

    /** What one component of the key counts. */
    public sealed interface Measure {
        long count(RankingInput input);

        /** Whether the count comes from replaying the input rather than from TLC's metrics. */
        default boolean replayed() {
            return false;
        }

        /** One of TLC's exploration metrics. A count TLC did not measure is 0. */
        record Exploration(ExplorationCount count) implements Measure {
            public Exploration {
                Objects.requireNonNull(count, "count");
            }

            @Override
            public long count(RankingInput input) {
                return input.metrics().count(count).orElse(0);
            }
        }

        /** A count that replaying the input yields. */
        enum Replayed implements Measure {
            /** The distinct temporal shapes of the property (ADR 0028 §2). */
            TEMPORAL_SHAPES;

            @Override
            public long count(RankingInput input) {
                return input.temporalShapes();
            }

            @Override
            public boolean replayed() {
                return true;
            }
        }
    }

    /** One component of the key: a count and how it is compared. */
    public record Component(Measure measure, Scale scale) {
        public Component {
            Objects.requireNonNull(measure, "measure");
            Objects.requireNonNull(scale, "scale");
        }

        Component(ExplorationCount count, Scale scale) {
            this(new Measure.Exploration(count), scale);
        }

        public long value(RankingInput input) {
            return scale.bucket(measure.count(input));
        }
    }

    private final List<Component> components;

    Ranking(List<Component> components) {
        this.components = List.copyOf(components);
    }

    /** Returns the key's components, in comparison order. */
    public List<Component> components() {
        return components;
    }

    /** Returns whether a component counts something that only replaying the input yields. */
    public boolean replays() {
        return components.stream().anyMatch(component -> component.measure().replayed());
    }

    /** Returns the value of every component of the key, in comparison order. */
    public List<Long> values(RankingInput input) {
        Objects.requireNonNull(input, "input");
        return components.stream().map(component -> component.value(input)).toList();
    }
}
