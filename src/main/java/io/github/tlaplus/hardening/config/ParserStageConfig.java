package io.github.tlaplus.hardening.config;

/** Capacity and timeout limits for the parser stage. */
public record ParserStageConfig(int maximumEntries, int timeoutSeconds) {
    public ParserStageConfig {
        ConfigValues.requireNonnegative(maximumEntries, "maximumEntries");
        ConfigValues.requirePositive(timeoutSeconds, "timeoutSeconds");
    }

    public static ParserStageConfig defaults() {
        return new ParserStageConfig(1_000, 30);
    }
}
