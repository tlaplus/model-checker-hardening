package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Admission-time facts about one corpus input: the generation that admitted it, its PBT cohort and
 * collection-richness score, and, for a mutant, the parent and operators it was derived from.
 *
 * <p>The workflow writes a generation on every input it admits. The field is optional here only so
 * that envelopes written before generations existed still decode; whether an entry without one is
 * usable is the workflow's decision (ADR 0010).
 *
 * <p>A quarantined candidate also lists the known-defect signatures it matched, in database order.
 * The first is its primary signature, which the quarantine cap and the statistics count it under.
 * An admitted input matched none, so the list is empty.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record GenerationMetadata(
        OptionalInt generation,
        int cohort,
        double richness,
        List<String> knownDefects,
        Optional<Mutation> mutation) {
    public GenerationMetadata {
        Objects.requireNonNull(generation, "generation");
        generation.ifPresent(value -> Preconditions.requireNonnegative(value, "generation"));
        Preconditions.requireNonnegative(cohort, "cohort");
        Preconditions.requireFiniteNonnegative(richness, "richness");
        knownDefects = List.copyOf(Objects.requireNonNull(knownDefects, "knownDefects"));
        Preconditions.require(
                knownDefects.stream().noneMatch(String::isBlank), "known-defect ids must not be blank");
        Preconditions.require(
                new HashSet<>(knownDefects).size() == knownDefects.size(),
                "known-defect ids must be distinct");
        Objects.requireNonNull(mutation, "mutation");
    }

    /** Returns the metadata of an input that PBT generated. */
    public static GenerationMetadata generated(int generation, int cohort, double richness) {
        return new GenerationMetadata(
                OptionalInt.of(generation), cohort, richness, List.of(), Optional.empty());
    }

    /** Returns the metadata of an input mutated from a parent. */
    public static GenerationMetadata mutated(
            int generation, int cohort, double richness, Mutation mutation) {
        return new GenerationMetadata(
                OptionalInt.of(generation),
                cohort,
                richness,
                List.of(),
                Optional.of(Objects.requireNonNull(mutation, "mutation")));
    }

    /** Returns this metadata for a candidate quarantined under the given signatures. */
    public GenerationMetadata withKnownDefects(List<String> signatures) {
        return new GenerationMetadata(generation, cohort, richness, signatures, mutation);
    }

    /** Returns the signature a quarantined candidate is capped and counted under. */
    public Optional<String> primaryKnownDefect() {
        return knownDefects.stream().findFirst();
    }
}
