package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.config.ApalacheStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.checker.CheckerWorker;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Parent-side handle for one persistent isolated Apalache JVM. */
final class ApalacheProcess implements CheckerWorker {
    private final Duration timeout;
    private final IsolatedWorkerProcess worker;

    private ApalacheProcess(Duration timeout, IsolatedWorkerProcess worker) {
        this.timeout = timeout;
        this.worker = worker;
    }

    static ApalacheProcess start(
            Path releaseJar,
            Path scratchDirectory,
            ApalacheStageConfig config,
            Duration timeout)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(releaseJar, "releaseJar");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(timeout, "timeout");
        var arguments = List.of(
                "-Xmx" + config.maximumHeapMegabytes() + "m",
                "-XX:+ExitOnOutOfMemoryError",
                "-XX:-UsePerfData");
        var worker = IsolatedWorkerProcess.start(
                scratchDirectory,
                timeout,
                ApalacheWorkerMain.class,
                List.of(releaseJar),
                arguments,
                "Apalache worker");
        return new ApalacheProcess(timeout, worker);
    }

    @Override
    public ToolResult check(String source) throws WorkflowException, InterruptedException {
        return worker.request(source, timeout);
    }

    @Override
    public void close() {
        worker.close();
    }
}
