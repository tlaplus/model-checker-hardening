package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * Sizes of generated collection values (ADR 0011).
 *
 * <p>Set and sequence literals and the closed terminals of collection types default to
 * {@code baseSize} elements; one input byte moves a literal's size within
 * {@code baseSize ± sizeSpread}. {@code maximumValueAtoms} bounds the elements of one value over
 * all nesting levels. {@code maximumSize} also bounds structural lists, which keep continuation
 * markers.
 *
 * @param maximumSize maximum elements in a variable-size collection
 * @param baseSize size that exhausted input decodes to
 * @param sizeSpread how far one size byte moves a size from the base
 * @param maximumValueAtoms atom budget of one collection literal or terminal
 */
public record CollectionLimits(int maximumSize, int baseSize, int sizeSpread, int maximumValueAtoms) {
    public static final int DEFAULT_MAXIMUM_SIZE = 8;
    public static final int DEFAULT_BASE_SIZE = 3;
    public static final int DEFAULT_SIZE_SPREAD = 4;
    public static final int DEFAULT_MAXIMUM_VALUE_ATOMS = 64;
    /** Largest spread whose {@code 2 * spread + 1} alternatives fit one byte. */
    public static final int MAXIMUM_SIZE_SPREAD = 127;

    public CollectionLimits {
        Preconditions.requirePositive(maximumSize, "maximumSize");
        Preconditions.require(baseSize >= 0 && baseSize <= maximumSize,
                "collection_base_size must be in the range 0..max_collection_size");
        Preconditions.require(sizeSpread >= 0 && sizeSpread <= MAXIMUM_SIZE_SPREAD,
                "collection_size_spread must be in the range 0.." + MAXIMUM_SIZE_SPREAD);
        Preconditions.requirePositive(maximumValueAtoms, "maximumValueAtoms");
    }

    public static CollectionLimits defaults() {
        return new CollectionLimits(DEFAULT_MAXIMUM_SIZE, DEFAULT_BASE_SIZE, DEFAULT_SIZE_SPREAD,
                DEFAULT_MAXIMUM_VALUE_ATOMS);
    }

    /** Returns these limits with another base size. */
    public CollectionLimits withBaseSize(int size) {
        return new CollectionLimits(maximumSize, size, sizeSpread, maximumValueAtoms);
    }
}
