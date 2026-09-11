package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * Capacity and resource limits for one model-checker stage.
 *
 * <p>TLC and Apalache take the same four settings and differ only in their defaults, so both are
 * configured by this record and told apart by the {@code CorpusStage} they are keyed by. The
 * per-checker defaults live on {@link CheckerProfile}.
 */
public record CheckerStageConfig(
        int maximumEntries,
        int timeoutSeconds,
        int maximumHeapMegabytes,
        int workers) implements StageLimits {
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAXIMUM_ENTRIES = 1_000;

    public CheckerStageConfig {
        Preconditions.requireNonnegative(maximumEntries, "maximumEntries");
        Preconditions.requirePositive(timeoutSeconds, "timeoutSeconds");
        Preconditions.requirePositive(maximumHeapMegabytes, "maximumHeapMegabytes");
        Preconditions.requirePositive(workers, "workers");
    }
}
