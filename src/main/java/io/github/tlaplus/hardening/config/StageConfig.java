package io.github.tlaplus.hardening.config;

/** Current-occupancy limit for one workflow stage. */
public record StageConfig(int maximumEntries) {
    public StageConfig {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("maximumEntries must be nonnegative");
        }
    }

    public static StageConfig defaults() {
        return new StageConfig(1_000);
    }
}
