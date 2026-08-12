package io.github.tlaplus.hardening.gen;

/** Resource limits for generated TLA+ expressions. */
public record IrGenerationConfig(
        int maximumTypeDepth,
        int maximumExpressionDepth,
        int maximumNodes,
        int maximumCollectionSize,
        int maximumStringBytes,
        int maximumIntegerBytes) {
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
    }

    public static IrGenerationConfig defaults() {
        return new IrGenerationConfig(3, 32, 16, 8, 32, 16);
    }
}
