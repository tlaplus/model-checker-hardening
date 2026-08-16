package io.github.tlaplus.hardening.config;

/** Capacity and resource limits for the TLC stage. */
public record TlcStageConfig(
        int maximumEntries,
        int timeoutSeconds,
        int maximumHeapMegabytes,
        int workers) {
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAXIMUM_HEAP_MEGABYTES = 512;
    public static final int DEFAULT_WORKERS = 1;
    public static final int DEFAULT_MAXIMUM_ENTRIES = 1_000;

    public TlcStageConfig {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("maximumEntries must be nonnegative");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        if (maximumHeapMegabytes <= 0) {
            throw new IllegalArgumentException("maximumHeapMegabytes must be positive");
        }
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive");
        }
    }

    public static TlcStageConfig defaults() {
        return new TlcStageConfig(
                DEFAULT_MAXIMUM_ENTRIES,
                DEFAULT_TIMEOUT_SECONDS,
                DEFAULT_MAXIMUM_HEAP_MEGABYTES,
                DEFAULT_WORKERS);
    }
}
