package io.github.tlaplus.hardening.common;

/** Shared validation of method and constructor arguments. */
public final class Preconditions {
    private Preconditions() {}

    /** Requires a nonnegative integer named by {@code name}. */
    public static void requireNonnegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be nonnegative");
        }
    }

    /** Requires a positive integer named by {@code name}. */
    public static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
