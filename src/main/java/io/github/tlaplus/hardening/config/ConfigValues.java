package io.github.tlaplus.hardening.config;

/** Shared range checks for configuration values, so one setting reads the same everywhere. */
final class ConfigValues {
    private ConfigValues() {}

    static void requireNonnegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be nonnegative");
        }
    }

    static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
