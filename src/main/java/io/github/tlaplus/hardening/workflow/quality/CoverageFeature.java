package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.common.ExprEdge;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One operator-coverage feature of an entry's evaluated code (ADR 0013): a construct or an edge
 * between constructs, with the hit-count bucket of its occurrences.
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

    /** Returns the features of the counted code. */
    static Set<CoverageFeature> of(ExprCounts counts) {
        var features = new HashSet<CoverageFeature>();
        counts.exprs().forEach((name, occurrences) -> features.add(new Construct(name, bucket(occurrences))));
        counts.edges().forEach((edge, occurrences) -> features.add(new Edge(edge, bucket(occurrences))));
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
