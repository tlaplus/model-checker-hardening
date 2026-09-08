package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;

/** Admission-time PBT cohort and collection-richness score for one corpus input. */
public record GenerationMetadata(int cohort, double richness) {
    public GenerationMetadata {
        Preconditions.requireNonnegative(cohort, "cohort");
        Preconditions.requireFiniteNonnegative(richness, "richness");
    }
}
