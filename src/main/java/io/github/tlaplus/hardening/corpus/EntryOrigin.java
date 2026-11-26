package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Which candidate source admitted an entry, as its generation metadata records it (ADR 0010, ADR
 * 0016 §6, ADR 0034 §6). {@link #SIZED} orders a generation's target ranges: mutants, adopted
 * entries, then PBT, which also fills a range whose source has no parents. Replays come on top.
 */
public enum EntryOrigin {
    /** A byte mutant of a parent that passed this corpus's quality gate. */
    MUTANT,
    /** The payload of a conformance parent of another corpus, with a rewrite appended. */
    ADOPTED,
    /** Drawn by property-based generation. */
    PBT,
    /**
     * A recorded counterexample of an entry of the previous generation (ADR 0034 §6). Replays come
     * on top of a generation's size.
     */
    REPLAY;

    /**
     * The origins that share a generation's {@code generation_size}, in the order their target
     * ranges are numbered; PBT is last and fills what the others leave.
     */
    public static final List<EntryOrigin> SIZED = List.of(MUTANT, ADOPTED, PBT);

    /** Returns how many of {@code admitted}, counted per origin, share a generation's size. */
    public static long sized(Map<EntryOrigin, Long> admitted) {
        Objects.requireNonNull(admitted, "admitted");
        return SIZED.stream().mapToLong(origin -> admitted.getOrDefault(origin, 0L)).sum();
    }

    /** Returns the origin the provenance of an entry records. */
    public static EntryOrigin of(Optional<Mutation> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (mutation.isEmpty()) {
            return PBT;
        }
        var operators = mutation.get().operators();
        if (operators.contains(MutationOperator.REPLAY)) {
            return REPLAY;
        }
        return operators.contains(MutationOperator.ADOPT) ? ADOPTED : MUTANT;
    }
}
