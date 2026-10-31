package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import io.github.tlaplus.hardening.workflow.worker.ChildJvm;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.JavaLaunch;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.WorkerSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapts fresh isolated TLC invocations to the shared tool stage. Every TLC stage runs this
 * backend; the stages differ only in their settings, such as the number of TLC workers.
 */
@ArchRole(Role.ADAPTER)
@Key
public final class TlcCheckerBackend implements ToolBackend {
    private final CorpusStage stage;
    private final CheckerStageConfig config;
    private final int workerCount;
    private final Path scratchDirectory;
    private final List<Path> classpath;

    /** {@code classpath} precedes TLC's own, so it can supply modules and their Java overrides. */
    public TlcCheckerBackend(
            CorpusStage stage, CheckerStageConfig config, int workerCount, Path scratchDirectory, List<Path> classpath) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.config = Objects.requireNonNull(config, "config");
        Preconditions.requirePositive(workerCount, "workerCount");
        this.workerCount = workerCount;
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        this.classpath = List.copyOf(classpath);
    }

    @Override
    public CorpusStage stage() {
        return stage;
    }

    @Override
    public int workerCount() {
        return workerCount;
    }

    @Override
    public int cpuPermits() {
        return config.workers();
    }

    @Override
    public ToolWorker startWorker() {
        return source -> check(scratchDirectory, classpath, source, config, config.timeout());
    }

    @Override
    public Optional<String> failureDetail(String diagnostic) {
        return TlcFailureDetail.extract(diagnostic);
    }

    /** Runs one TLC request in a fresh isolated JVM. */
    static ToolResult check(
            Path scratchDirectory,
            List<Path> classpath,
            ToolInput source,
            CheckerStageConfig config,
            Duration timeout)
            throws WorkflowException, InterruptedException {
        try (var worker = IsolatedWorkerProcess.start(new WorkerSpec(
                scratchDirectory,
                timeout,
                TlcWorkerMain.class,
                classpath,
                new ChildJvm(
                        config.workers(),
                        ChildJvm.Compilation.QUICK,
                        JavaLaunch.boundedHeap(
                                config.maximumHeapMegabytes(),
                                "-D" + TlcWorkerMain.WORKERS_PROPERTY + "=" + config.workers())),
                "TLC worker"))) {
            return worker.request(source, timeout);
        }
    }
}
