package io.github.tlaplus.hardening.gen;

/**
 * Bounds on recursive construction and variable-size payloads within one expression.
 *
 * <p>These limits bound construction independently of the input length: they are what stops an
 * endless run of continuation markers, and what makes an exhausted cursor produce a small default
 * rather than a failure.
 *
 * @param maximumTypeDepth maximum nesting depth of a generated type
 * @param maximumExpressionDepth maximum recursive expression depth
 * @param maximumNodes maximum nonterminal expression requests per top-level body
 * @param maximumCollectionSize maximum elements in a variable-size collection
 * @param maximumStringBytes maximum byte payload mapped into a string literal
 * @param maximumIntegerBytes maximum two's-complement payload for an integer literal
 */
public record ExpressionLimits(
        int maximumTypeDepth,
        int maximumExpressionDepth,
        int maximumNodes,
        int maximumCollectionSize,
        int maximumStringBytes,
        int maximumIntegerBytes) {

    public static final int DEFAULT_MAXIMUM_TYPE_DEPTH = 3;
    public static final int DEFAULT_MAXIMUM_EXPRESSION_DEPTH = 32;
    public static final int DEFAULT_MAXIMUM_NODES = 128;
    public static final int DEFAULT_MAXIMUM_COLLECTION_SIZE = 8;
    public static final int DEFAULT_MAXIMUM_STRING_BYTES = 32;
    public static final int DEFAULT_MAXIMUM_INTEGER_BYTES = 16;

    public ExpressionLimits {
        if (maximumTypeDepth < 0) {
            throw new IllegalArgumentException("maximumTypeDepth must be nonnegative");
        }
        if (maximumExpressionDepth < 1) {
            throw new IllegalArgumentException("maximumExpressionDepth must be positive");
        }
        if (maximumNodes < 1) {
            throw new IllegalArgumentException("maximumNodes must be positive");
        }
        if (maximumCollectionSize < 1) {
            throw new IllegalArgumentException("maximumCollectionSize must be positive");
        }
        if (maximumStringBytes < 0) {
            throw new IllegalArgumentException("maximumStringBytes must be nonnegative");
        }
        if (maximumIntegerBytes < 0) {
            throw new IllegalArgumentException("maximumIntegerBytes must be nonnegative");
        }
    }

    public static ExpressionLimits defaults() {
        return new ExpressionLimits(
                DEFAULT_MAXIMUM_TYPE_DEPTH,
                DEFAULT_MAXIMUM_EXPRESSION_DEPTH,
                DEFAULT_MAXIMUM_NODES,
                DEFAULT_MAXIMUM_COLLECTION_SIZE,
                DEFAULT_MAXIMUM_STRING_BYTES,
                DEFAULT_MAXIMUM_INTEGER_BYTES);
    }
}
