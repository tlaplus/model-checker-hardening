package io.github.tlaplus.hardening.corpus;

import java.util.Objects;

/**
 * How a workflow run aggregates checker verdicts. The workflow derives it from the corpus records
 * it has verified; the corpus only applies it.
 */
public record AggregationPolicy(Oracle oracle) {
    /** The policy of a conformance corpus, which is what every corpus was before ADR 0016. */
    public static final AggregationPolicy CONFORMANCE = new AggregationPolicy(Oracle.CONFORMANCE);

    public AggregationPolicy {
        Objects.requireNonNull(oracle, "oracle");
    }

    /** Returns the policy of a corpus that runs {@code technique}. */
    public static AggregationPolicy of(Technique technique) {
        return new AggregationPolicy(technique.oracle());
    }
}
