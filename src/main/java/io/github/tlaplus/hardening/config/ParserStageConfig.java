package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;

/** Capacity and timeout limits for the parser stage. */
public record ParserStageConfig(int maximumEntries, int timeoutSeconds) implements StageLimits {
    public ParserStageConfig {
        Preconditions.requireNonnegative(maximumEntries, "maximumEntries");
        Preconditions.requirePositive(timeoutSeconds, "timeoutSeconds");
    }

    public static ParserStageConfig defaults() {
        return new ParserStageConfig(1_000, 30);
    }
}
