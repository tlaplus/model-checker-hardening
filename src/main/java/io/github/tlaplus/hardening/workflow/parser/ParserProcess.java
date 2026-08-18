package io.github.tlaplus.hardening.workflow.parser;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.WorkerSpec;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Parent-side handle for one persistent isolated SANY JVM. */
final class ParserProcess implements AutoCloseable {
    private final IsolatedWorkerProcess worker;

    private ParserProcess(IsolatedWorkerProcess worker) {
        this.worker = worker;
    }

    static ParserProcess start(Path scratchDirectory, Duration timeout)
            throws WorkflowException, InterruptedException {
        return new ParserProcess(IsolatedWorkerProcess.start(
                new WorkerSpec(scratchDirectory, timeout, ParserWorkerMain.class, "parser worker")));
    }

    ToolResult parse(String source, Duration timeout)
            throws WorkflowException, InterruptedException {
        return worker.request(source, timeout);
    }

    @Override
    public void close() {
        worker.close();
    }
}
