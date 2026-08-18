package io.github.tlaplus.hardening.config;

/**
 * Capacity and resource limits for one model-checker stage.
 *
 * <p>TLC and Apalache take the same four settings and differ only in their defaults, so both are
 * configured by this record and told apart by the {@code CorpusStage} they are keyed by.
 */
public record CheckerStageConfig(
        int maximumEntries,
        int timeoutSeconds,
        int maximumHeapMegabytes,
        int workers) {
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAXIMUM_HEAP_MEGABYTES = 512;
    public static final int DEFAULT_MAXIMUM_ENTRIES = 1_000;

    /** TLC runs its own workers inside one JVM, so one FuzzTLA invocation is the default. */
    public static final int DEFAULT_TLC_WORKERS = 1;

    /** Apalache runs one input per worker JVM, so it defaults to half the visible processors. */
    public static final int DEFAULT_APALACHE_WORKERS =
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    public CheckerStageConfig {
        ConfigValues.requireNonnegative(maximumEntries, "maximumEntries");
        ConfigValues.requirePositive(timeoutSeconds, "timeoutSeconds");
        ConfigValues.requirePositive(maximumHeapMegabytes, "maximumHeapMegabytes");
        ConfigValues.requirePositive(workers, "workers");
    }

    public static CheckerStageConfig tlcDefaults() {
        return defaults(DEFAULT_TLC_WORKERS);
    }

    public static CheckerStageConfig apalacheDefaults() {
        return defaults(DEFAULT_APALACHE_WORKERS);
    }

    private static CheckerStageConfig defaults(int workers) {
        return new CheckerStageConfig(
                DEFAULT_MAXIMUM_ENTRIES,
                DEFAULT_TIMEOUT_SECONDS,
                DEFAULT_MAXIMUM_HEAP_MEGABYTES,
                workers);
    }
}
