package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.config.ApalacheStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.checker.CheckerBackend;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Adapts fresh Apalache CLI invocations to the shared checker stage. */
public final class ApalacheCheckerBackend implements CheckerBackend {
    private final ApalacheStageConfig config;
    private final Path releaseJar;
    private final Path scratchDirectory;

    public ApalacheCheckerBackend(
            ApalacheStageConfig config, Path releaseJar, Path scratchDirectory) {
        this.config = Objects.requireNonNull(config, "config");
        this.releaseJar = Objects.requireNonNull(releaseJar, "releaseJar");
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
    }

    @Override
    public String name() {
        return "apalache";
    }

    @Override
    public String displayName() {
        return "Apalache";
    }

    @Override
    public int maximumEntries() {
        return config.maximumEntries();
    }

    @Override
    public int workerCount() {
        return config.workers();
    }

    @Override
    public int cpuPermits() {
        return 1;
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds());
    }

    @Override
    public ToolResult check(String source) throws WorkflowException, InterruptedException {
        return ApalacheProcess.check(
                releaseJar, scratchDirectory, source, config, timeout());
    }

    @Override
    public Optional<String> failureDetail(String diagnostic) {
        return ApalacheFailureDetail.extract(diagnostic);
    }
}
