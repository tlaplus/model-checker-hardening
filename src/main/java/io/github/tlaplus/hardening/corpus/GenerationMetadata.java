package io.github.tlaplus.hardening.corpus;

/** Admission-time PBT cohort and collection-richness score for one corpus input. */
public record GenerationMetadata(int cohort, double richness) {
    public GenerationMetadata {
        if (cohort < 0) {
            throw new IllegalArgumentException("cohort must be nonnegative");
        }
        if (!Double.isFinite(richness) || richness < 0.0) {
            throw new IllegalArgumentException("richness must be finite and nonnegative");
        }
    }
}
