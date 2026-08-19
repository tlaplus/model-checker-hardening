package io.github.tlaplus.hardening.config;

/** Current-occupancy limit for one workflow stage. */
public record StageConfig(int maximumEntries) {
    public StageConfig {
        ConfigValues.requireNonnegative(maximumEntries, "maximumEntries");
    }

    public static StageConfig defaults() {
        return new StageConfig(1_000);
    }
}
