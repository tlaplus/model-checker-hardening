package io.github.tlaplus.hardening.config;

/** Capacity and timeout limits for the parser stage. */
public record ParserStageConfig(int maximumEntries, int timeoutSeconds) {
    public ParserStageConfig {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("maximumEntries must be nonnegative");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
    }

    public static ParserStageConfig defaults() {
        return new ParserStageConfig(1_000, 30);
    }
}
