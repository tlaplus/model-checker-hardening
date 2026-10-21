package io.github.tlaplus.hardening.workflow.tool;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;

/** Worker-local tool state used for one or more sequential inputs. */
@ArchRole(Role.CONTRACT)
@Key
@FunctionalInterface
public interface ToolWorker extends AutoCloseable {
    ToolResult check(ToolInput input) throws WorkflowException, InterruptedException;

    @Override
    default void close() {}
}
