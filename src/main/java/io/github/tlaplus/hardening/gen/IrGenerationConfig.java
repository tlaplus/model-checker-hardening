package io.github.tlaplus.hardening.gen;

import java.util.Objects;
import java.util.Set;

/** Settings and resource limits for generated TLA+ expressions. */
public record IrGenerationConfig(
        int maximumTypeDepth,
        int maximumExpressionDepth,
        int maximumNodes,
        int maximumCollectionSize,
        int maximumStringBytes,
        int maximumIntegerBytes,
        Set<ExpressionCategory> ignoredCategories) {

    public static final int DEFAULT_MAXIMUM_TYPE_DEPTH = 3;
    public static final int DEFAULT_MAXIMUM_EXPRESSION_DEPTH = 32;
    public static final int DEFAULT_MAXIMUM_NODES = 32;
    public static final int DEFAULT_MAXIMUM_COLLECTION_SIZE = 8;
    public static final int DEFAULT_MAXIMUM_STRING_BYTES = 32;
    public static final int DEFAULT_MAXIMUM_INTEGER_BYTES = 16;

    public IrGenerationConfig {
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
        ignoredCategories = Set.copyOf(
                Objects.requireNonNull(ignoredCategories, "ignoredCategories"));
        if (ignoredCategories.stream().anyMatch(category -> !category.isIgnorable())) {
            throw new IllegalArgumentException("the core expression category cannot be ignored");
        }
    }

    public static IrGenerationConfig defaults() {
        return new IrGenerationConfig(
                DEFAULT_MAXIMUM_TYPE_DEPTH,
                DEFAULT_MAXIMUM_EXPRESSION_DEPTH,
                DEFAULT_MAXIMUM_NODES,
                DEFAULT_MAXIMUM_COLLECTION_SIZE,
                DEFAULT_MAXIMUM_STRING_BYTES,
                DEFAULT_MAXIMUM_INTEGER_BYTES,
                Set.of(
                        ExpressionCategory.ACTION,
                        ExpressionCategory.TEMPORAL,
                        ExpressionCategory.UNBOUND,
                        ExpressionCategory.EXOTIC));
    }
}
