package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.tool.ProcessToolWorker;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import io.github.tlaplus.hardening.workflow.worker.ChildJvm;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.JavaLaunch;
import io.github.tlaplus.hardening.workflow.worker.WorkerSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Adapts persistent Apalache tool workers to the shared tool stage. */
@ArchRole(Role.ADAPTER)
@Key
public final class ApalacheCheckerBackend implements ToolBackend {
    private final CheckerStageConfig config;
    private final Path releaseJar;
    private final Path scratchDirectory;

    public ApalacheCheckerBackend(
            CheckerStageConfig config, Path releaseJar, Path scratchDirectory) {
        this.config = Objects.requireNonNull(config, "config");
        this.releaseJar = Objects.requireNonNull(releaseJar, "releaseJar");
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
    }

    @Override
    public CorpusStage stage() {
        return CorpusStage.APALACHE;
    }

    @Override
    public int workerCount() {
        return config.workers();
    }

    @Override
    public int cpuPermits() {
        return 1;
    }

    @Override
    public Function<SpecArtifact, String> renderer() {
        // Apalache evaluates the self-contained module, never instance aliases (ADR 0014).
        return artifact -> ApalacheIrJson.render(artifact.module());
    }

    @Override
    public ToolWorker startWorker() throws WorkflowException, InterruptedException {
        return start(releaseJar, scratchDirectory, config, config.timeout());
    }

    @Override
    public Optional<String> failureDetail(String diagnostic) {
        return ApalacheFailureDetail.extract(diagnostic);
    }

    /** Starts one persistent isolated Apalache JVM, which serves inputs until a crash retires it. */
    static ToolWorker start(Path releaseJar, Path scratchDirectory, CheckerStageConfig config, Duration timeout)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(releaseJar, "releaseJar");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(timeout, "timeout");
        var process = IsolatedWorkerProcess.start(new WorkerSpec(
                scratchDirectory,
                timeout,
                ApalacheWorkerMain.class,
                List.of(releaseJar),
                new ChildJvm(
                        1,
                        ChildJvm.Compilation.TIERED,
                        JavaLaunch.boundedHeap(config.maximumHeapMegabytes(), "-XX:-UsePerfData")),
                "Apalache worker"));
        return new ProcessToolWorker(process, timeout);
    }
}
