package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Objects;

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
 * @param collections sizes of collections and bounds on variable-size lists
 * @param maximumStringBytes maximum byte payload mapped into a string literal
 * @param integers integer literals and the closed integer terminal
 */
public record ExpressionLimits(
        int maximumTypeDepth,
        int maximumExpressionDepth,
        int maximumNodes,
        CollectionLimits collections,
        int maximumStringBytes,
        IntegerLimits integers) {

    public static final int DEFAULT_MAXIMUM_TYPE_DEPTH = 3;
    public static final int DEFAULT_MAXIMUM_EXPRESSION_DEPTH = 32;
    public static final int DEFAULT_MAXIMUM_NODES = 128;
    public static final int DEFAULT_MAXIMUM_STRING_BYTES = 32;

    public ExpressionLimits {
        Preconditions.requireNonnegative(maximumTypeDepth, "maximumTypeDepth");
        Preconditions.requirePositive(maximumExpressionDepth, "maximumExpressionDepth");
        Preconditions.requirePositive(maximumNodes, "maximumNodes");
        Objects.requireNonNull(collections, "collections");
        Preconditions.requireNonnegative(maximumStringBytes, "maximumStringBytes");
        Objects.requireNonNull(integers, "integers");
    }

    /** Returns these limits with other collection limits. */
    public ExpressionLimits withCollections(CollectionLimits limits) {
        return new ExpressionLimits(maximumTypeDepth, maximumExpressionDepth, maximumNodes, limits,
                maximumStringBytes, integers);
    }

    public static ExpressionLimits defaults() {
        return new ExpressionLimits(
                DEFAULT_MAXIMUM_TYPE_DEPTH,
                DEFAULT_MAXIMUM_EXPRESSION_DEPTH,
                DEFAULT_MAXIMUM_NODES,
                CollectionLimits.defaults(),
                DEFAULT_MAXIMUM_STRING_BYTES,
                IntegerLimits.defaults());
    }
}
