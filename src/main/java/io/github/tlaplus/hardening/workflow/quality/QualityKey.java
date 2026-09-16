package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongUnaryOperator;

/**
 * The order in which the quality gate ranks admissible entries, best first (ADR 0010).
 *
 * <p>The key compares TLC's exploration counts lexicographically, higher first. Counts that grow
 * without a bound are compared by logarithmic bucket, so that a small difference cannot outrank a
 * real difference in a later component. A count TLC did not measure ranks as 0. Ties go to the
 * shorter input and then to the smaller digest, so the order is total.
 */
final class QualityKey {
    /** How a count is compared. */
    enum Scale {
        LINEAR(value -> value),
        /** {@code floor(log2(value + 1))}. */
        LOG2(value -> Long.SIZE - 1 - Long.numberOfLeadingZeros(value + 1));

        private final LongUnaryOperator bucket;

        Scale(LongUnaryOperator bucket) {
            this.bucket = bucket;
        }

        long bucket(long value) {
            return bucket.applyAsLong(value);
        }
    }

    /** One component of the key. */
    record Component(ExplorationCount count, Scale scale) {
        long value(ExplorationMetrics metrics) {
            return scale.bucket(metrics.count(count).orElse(0));
        }
    }

    /** The key's components in comparison order. */
    static final List<Component> COMPONENTS = List.of(
            new Component(ExplorationCount.PROJECTED_DEPTH, Scale.LINEAR),
            new Component(ExplorationCount.PROJECTED_STATES, Scale.LOG2),
            new Component(ExplorationCount.ACTIONS_DISCOVERING, Scale.LINEAR),
            new Component(ExplorationCount.MAX_STATE_NODES, Scale.LOG2),
            new Component(ExplorationCount.MAX_CARDINALITY, Scale.LOG2),
            new Component(ExplorationCount.MAX_NESTING, Scale.LINEAR));

    /** What the key ranks: an entry's TLC metrics, its input length and its digest. */
    record Ranked(ExplorationMetrics metrics, int inputBytes, String digest) {
        Ranked {
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(digest, "digest");
        }
    }

    /** Orders the best entry first. */
    static final Comparator<Ranked> BEST_FIRST = bestFirst();

    private QualityKey() {}

    private static Comparator<Ranked> bestFirst() {
        Comparator<Ranked> order = (left, right) -> 0;
        for (var component : COMPONENTS) {
            order = order.thenComparing(Comparator.comparingLong(
                    (Ranked ranked) -> component.value(ranked.metrics())).reversed());
        }
        return order.thenComparingInt(Ranked::inputBytes).thenComparing(Ranked::digest);
    }
}
