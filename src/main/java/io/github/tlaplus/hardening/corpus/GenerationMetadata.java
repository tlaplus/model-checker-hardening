package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Admission-time PBT cohort and collection-richness score for one corpus input.
 *
 * <p>A quarantined candidate also lists the known-defect signatures it matched, in database order.
 * The first is its primary signature, which the quarantine cap and the statistics count it under.
 * An admitted input matched none, so the list is empty.
 */
public record GenerationMetadata(int cohort, double richness, List<String> knownDefects) {
    public GenerationMetadata {
        Preconditions.requireNonnegative(cohort, "cohort");
        Preconditions.requireFiniteNonnegative(richness, "richness");
        knownDefects = List.copyOf(Objects.requireNonNull(knownDefects, "knownDefects"));
        Preconditions.require(
                knownDefects.stream().noneMatch(String::isBlank), "known-defect ids must not be blank");
        Preconditions.require(
                new HashSet<>(knownDefects).size() == knownDefects.size(),
                "known-defect ids must be distinct");
    }

    /** Returns the metadata of an admitted input. */
    public GenerationMetadata(int cohort, double richness) {
        this(cohort, richness, List.of());
    }

    /** Returns the signature a quarantined candidate is capped and counted under. */
    public Optional<String> primaryKnownDefect() {
        return knownDefects.stream().findFirst();
    }
}
