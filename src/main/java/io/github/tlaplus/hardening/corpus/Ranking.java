package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
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
    /** Depth of state change first (ADR 0010). */
    DEPTH(List.of(
            new Component(ExplorationCount.PROJECTED_DEPTH, Scale.LINEAR),
            new Component(ExplorationCount.PROJECTED_STATES, Scale.LOG2),
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

    /** One component of the key: a count and how it is compared. A count not measured is 0. */
    public record Component(ExplorationCount count, Scale scale) {
        public Component {
            Objects.requireNonNull(count, "count");
            Objects.requireNonNull(scale, "scale");
        }

        public long value(ExplorationMetrics metrics) {
            return scale.bucket(metrics.count(count).orElse(0));
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

    /** Returns the value of every component of the key, in comparison order. */
    public List<Long> values(ExplorationMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics");
        return components.stream().map(component -> component.value(metrics)).toList();
    }
}
