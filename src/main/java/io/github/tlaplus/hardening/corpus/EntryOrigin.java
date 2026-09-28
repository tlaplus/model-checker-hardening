package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.util.Objects;
import java.util.Optional;

/**
 * Which candidate source admitted an entry, as its generation metadata records it (ADR 0010, ADR
 * 0016 §6). Declaration order is the order of a generation's target ranges: mutants, adopted
 * entries, then PBT, which also fills a range whose source has no parents.
 */
public enum EntryOrigin {
    /** A byte mutant of a parent that passed this corpus's quality gate. */
    MUTANT,
    /** The payload of a conformance parent of another corpus, with a rewrite appended. */
    ADOPTED,
    /** Drawn by property-based generation. */
    PBT;

    /** Returns the origin the provenance of an entry records. */
    public static EntryOrigin of(Optional<Mutation> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (mutation.isEmpty()) {
            return PBT;
        }
        return mutation.get().operators().contains(MutationOperator.ADOPT) ? ADOPTED : MUTANT;
    }
}
