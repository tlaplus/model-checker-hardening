package io.github.tlaplus.hardening.workflow.checker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;

/** Worker-local model-checker state used for one or more sequential inputs. */
@FunctionalInterface
public interface CheckerWorker extends AutoCloseable {
    ToolResult check(String source) throws WorkflowException, InterruptedException;

    @Override
    default void close() {}
}
