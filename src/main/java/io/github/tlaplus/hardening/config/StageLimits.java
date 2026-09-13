package io.github.tlaplus.hardening.config;

import java.time.Duration;

/** The limits every configured tool stage takes: its result capacity and a per-input time limit. */
public interface StageLimits {
    /** Maximum combined occupancy of the stage's result directories. */
    int maximumEntries();

    /** Wall-clock limit for processing one input, in seconds. */
    int timeoutSeconds();

    default Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds());
    }
}
