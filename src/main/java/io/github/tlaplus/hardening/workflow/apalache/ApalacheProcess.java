package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.tool.ProcessToolWorker;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.JavaLaunch;
import io.github.tlaplus.hardening.workflow.worker.WorkerSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Starts one persistent isolated Apalache JVM, which serves inputs until a crash retires it. */
final class ApalacheProcess {
    private ApalacheProcess() {}

    static ToolWorker start(
            Path releaseJar,
            Path scratchDirectory,
            CheckerStageConfig config,
            Duration timeout)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(releaseJar, "releaseJar");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(timeout, "timeout");
        var process = IsolatedWorkerProcess.start(new WorkerSpec(
                scratchDirectory,
                timeout,
                ApalacheWorkerMain.class,
                List.of(releaseJar),
                JavaLaunch.boundedHeap(config.maximumHeapMegabytes(), "-XX:-UsePerfData"),
                "Apalache worker"));
        return new ProcessToolWorker(process, timeout);
    }
}
