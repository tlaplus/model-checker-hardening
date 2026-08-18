package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.config.TlcStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.checker.CheckerBackend;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Adapts fresh isolated TLC invocations to the shared checker stage. */
public final class TlcCheckerBackend implements CheckerBackend {
    private final TlcStageConfig config;
    private final int workerCount;
    private final Path scratchDirectory;

    public TlcCheckerBackend(
            TlcStageConfig config, int workerCount, Path scratchDirectory) {
        this.config = Objects.requireNonNull(config, "config");
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        this.workerCount = workerCount;
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
    }

    @Override
    public String name() {
        return "tlc";
    }

    @Override
    public String displayName() {
        return "TLC";
    }

    @Override
    public int maximumEntries() {
        return config.maximumEntries();
    }

    @Override
    public int workerCount() {
        return workerCount;
    }

    @Override
    public int cpuPermits() {
        return config.workers();
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds());
    }

    @Override
    public ToolResult check(String source) throws WorkflowException, InterruptedException {
        return TlcProcess.check(scratchDirectory, source, config, timeout());
    }

    @Override
    public Optional<String> failureDetail(String diagnostic) {
        return TlcFailureDetail.extract(diagnostic);
    }
}
