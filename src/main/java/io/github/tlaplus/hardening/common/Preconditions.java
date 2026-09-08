package io.github.tlaplus.hardening.common;

/** Shared validation of method and constructor arguments. */
public final class Preconditions {
    private Preconditions() {}

    /** Requires a domain condition, preserving the caller's diagnostic verbatim. */
    public static void require(boolean condition, String diagnostic) {
        if (!condition) {
            throw new IllegalArgumentException(diagnostic);
        }
    }

    /** Requires a nonnegative integer named by {@code name}. */
    public static void requireNonnegative(long value, String name) {
        require(value >= 0, name + " must be nonnegative");
    }

    /** Requires a finite, nonnegative floating-point value named by {@code name}. */
    public static void requireFiniteNonnegative(double value, String name) {
        require(Double.isFinite(value) && !(value < 0.0), name + " must be finite and nonnegative");
    }

    /** Requires a positive integer named by {@code name}. */
    public static void requirePositive(int value, String name) {
        require(value > 0, name + " must be positive");
    }
}
