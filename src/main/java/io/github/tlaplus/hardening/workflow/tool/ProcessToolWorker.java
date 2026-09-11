package io.github.tlaplus.hardening.workflow.tool;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.IsolatedWorkerProcess;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.time.Duration;
import java.util.Objects;

/** A tool worker served by one isolated child process until a crash verdict retires it. */
public final class ProcessToolWorker implements ToolWorker {
    private final IsolatedWorkerProcess process;
    private final Duration timeout;

    /** @param timeout the wall-clock limit of every request to {@code process} */
    public ProcessToolWorker(IsolatedWorkerProcess process, Duration timeout) {
        this.process = Objects.requireNonNull(process, "process");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public ToolResult check(ToolInput input) throws WorkflowException, InterruptedException {
        return process.request(input, timeout);
    }

    @Override
    public void close() {
        process.close();
    }
}
