package io.github.tlaplus.hardening.config;

/** Capacity and timeout limits for the parser stage. */
public record ParserConfig(int maximumEntries, int timeoutSeconds) {
    public ParserConfig {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("maximumEntries must be nonnegative");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
    }

    public static ParserConfig defaults() {
        return new ParserConfig(1_000, 30);
    }
}
