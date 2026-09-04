package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;

/** Current-occupancy limit for one workflow stage. */
public record StageConfig(int maximumEntries) {
    public StageConfig {
        Preconditions.requireNonnegative(maximumEntries, "maximumEntries");
    }

    public static StageConfig defaults() {
        return new StageConfig(1_000);
    }
}
