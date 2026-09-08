package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;

/** Limits and collection-richness controls for property-based input generation. */
public record PbtConfig(
        int maximumInputBytes,
        int richnessCohorts,
        double richnessNestingBase,
        double richnessThresholdBase) {
    private static final int DEFAULT_RICHNESS_COHORTS = 10;
    private static final double DEFAULT_RICHNESS_NESTING_BASE = 2.0;
    private static final double DEFAULT_RICHNESS_THRESHOLD_BASE = 1.5;

    public PbtConfig {
        Preconditions.requireNonnegative(maximumInputBytes, "maximumInputBytes");
        Preconditions.requirePositive(richnessCohorts, "richnessCohorts");
        Preconditions.require(Double.isFinite(richnessNestingBase) && richnessNestingBase >= 1.0,
                "richnessNestingBase must be finite and at least 1");
        Preconditions.require(Double.isFinite(richnessThresholdBase) && richnessThresholdBase > 1.0,
                "richnessThresholdBase must be finite and greater than 1");

        var previous = 0.0;
        for (var cohort = 1; cohort < richnessCohorts; cohort++) {
            var threshold = StrictMath.pow(richnessThresholdBase, cohort - 1);
            Preconditions.require(Double.isFinite(threshold) && threshold > previous,
                    "richness thresholds must be finite and strictly increasing");
            previous = threshold;
        }
    }

    /** Returns the limits written by {@code fuzztla init}. */
    public static PbtConfig defaults() {
        return new PbtConfig(
                10_240,
                DEFAULT_RICHNESS_COHORTS,
                DEFAULT_RICHNESS_NESTING_BASE,
                DEFAULT_RICHNESS_THRESHOLD_BASE);
    }

    /** Returns the minimum collection-richness score accepted by {@code cohort}. */
    public double richnessThreshold(int cohort) {
        Preconditions.require(cohort >= 0 && cohort < richnessCohorts,
                "cohort must be in the range 0.." + (richnessCohorts - 1));
        return cohort == 0 ? 0.0 : StrictMath.pow(richnessThresholdBase, cohort - 1);
    }

    /** Checks the finite input space without overflowing while calculating its cardinality. */
    boolean supportsDistinctInputs(int target) {
        long total = 1;
        long atLength = 1;
        for (var length = 1; length <= maximumInputBytes && total < target; length++) {
            atLength = Math.min(target, atLength * 256);
            total = Math.min(target, total + atLength);
        }
        return total >= target;
    }
}
