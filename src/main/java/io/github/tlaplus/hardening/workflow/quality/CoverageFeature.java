package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.common.ExprEdge;
import io.github.tlaplus.hardening.corpus.ReplayedInput;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One coverage feature of an entry, with the hit-count bucket of its occurrences: a construct or an
 * edge between constructs of its evaluated code (ADR 0013), or a temporal shape of its property
 * (ADR 0028 §3).
 */
sealed interface CoverageFeature {
    record Construct(String name, int bucket) implements CoverageFeature {
        public Construct {
            Objects.requireNonNull(name, "name");
        }
    }

    record Edge(ExprEdge edge, int bucket) implements CoverageFeature {
        public Edge {
            Objects.requireNonNull(edge, "edge");
        }
    }

    /** A temporal shape: an edge between temporal operators that read the state. */
    record TemporalShape(ExprEdge shape, int bucket) implements CoverageFeature {
        public TemporalShape {
            Objects.requireNonNull(shape, "shape");
        }
    }

    /** Returns the features of a replayed input. */
    static Set<CoverageFeature> of(ReplayedInput replayed) {
        var features = new HashSet<CoverageFeature>();
        var counts = replayed.counts();
        counts.exprs().forEach((name, occurrences) -> features.add(new Construct(name, bucket(occurrences))));
        counts.edges().forEach((edge, occurrences) -> features.add(new Edge(edge, bucket(occurrences))));
        replayed.temporalShapes().forEach((shape, occurrences) ->
                features.add(new TemporalShape(shape, bucket(occurrences))));
        return Set.copyOf(features);
    }

    /**
     * AFL's hit-count bucket: 1, 2, 3, 4–7, 8–15, 16–31, 32–127 and at least 128 occurrences map to
     * buckets 1 to 8; 0 maps to 0.
     */
    static int bucket(long occurrences) {
        if (occurrences <= 3) {
            return (int) Math.max(0, occurrences);
        }
        if (occurrences < 32) {
            // 4–7, 8–15 and 16–31 have floor(log2) 2, 3 and 4.
            var log2 = Long.SIZE - 1 - Long.numberOfLeadingZeros(occurrences);
            return 2 + log2;
        }
        return occurrences < 128 ? 7 : 8;
    }
}
