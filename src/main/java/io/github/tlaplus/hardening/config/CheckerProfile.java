package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * What differs between the configured model checkers: the defaults of their resource settings and
 * the documentation of the settings whose meaning follows from the worker lifecycle. TLC runs one
 * child JVM per input with its own worker threads; Apalache keeps one persistent child JVM per
 * FuzzTLA worker.
 *
 * <p>Every other fact about a checker table is shared by {@link CheckerStageConfig} and {@link
 * ConfigSchema}, so adding a checker adds one constant here.
 */
public enum CheckerProfile {
    TLC(
            CorpusStage.TLC,
            512,
            1,
            "Maximum heap allocated to each isolated TLC JVM.",
            List.of("Number of TLC model-checking workers in each isolated JVM.")),
    APALACHE(
            CorpusStage.APALACHE,
            1_024,
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            "Maximum heap allocated to each persistent Apalache worker JVM.",
            List.of(
                    "Number of concurrent FuzzTLA Apalache workers.",
                    "Initialized to half the available processors, rounded down (at least one)."));

    private final CorpusStage stage;
    private final int maximumHeapMegabytes;
    private final int workers;
    private final String heapDocumentation;
    private final List<String> workersDocumentation;

    CheckerProfile(
            CorpusStage stage,
            int maximumHeapMegabytes,
            int workers,
            String heapDocumentation,
            List<String> workersDocumentation) {
        this.stage = stage;
        this.maximumHeapMegabytes = maximumHeapMegabytes;
        this.workers = workers;
        this.heapDocumentation = heapDocumentation;
        this.workersDocumentation = workersDocumentation;
    }

    /** Returns the profile of one checker stage. */
    public static CheckerProfile of(CorpusStage stage) {
        Objects.requireNonNull(stage, "stage");
        return Arrays.stream(values())
                .filter(profile -> profile.stage == stage)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(stage + " is not a checker stage"));
    }

    public CorpusStage stage() {
        return stage;
    }

    /** Returns the settings {@code fuzztla init} writes for this checker. */
    public CheckerStageConfig defaults() {
        return new CheckerStageConfig(
                CheckerStageConfig.DEFAULT_MAXIMUM_ENTRIES,
                CheckerStageConfig.DEFAULT_TIMEOUT_SECONDS,
                maximumHeapMegabytes,
                workers);
    }

    String heapDocumentation() {
        return heapDocumentation;
    }

    List<String> workersDocumentation() {
        return workersDocumentation;
    }
}
