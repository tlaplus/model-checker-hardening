package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.config.TlcStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Runs one TLC request in a fresh isolated JVM. */
final class TlcProcess {
    private TlcProcess() {}

    static ToolResult check(
            Path scratchDirectory,
            String source,
            TlcStageConfig config,
            Duration timeout)
            throws WorkflowException, InterruptedException {
        var arguments = List.of(
                "-Xmx" + config.maximumHeapMegabytes() + "m",
                "-XX:+ExitOnOutOfMemoryError",
                "-D" + TlcWorkerMain.WORKERS_PROPERTY + "=" + config.workers());
        try (var worker = IsolatedWorkerProcess.start(
                scratchDirectory,
                timeout,
                TlcWorkerMain.class,
                arguments,
                "TLC worker")) {
            return worker.request(source, timeout);
        }
    }
}
