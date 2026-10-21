package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;

/** An independently scheduled workflow stage that owns its worker threads. */
@ArchRole(Role.CONTRACT)
@Key
public interface WorkflowStage extends AutoCloseable {
    String name();

    void start();

    void await() throws InterruptedException;

    @Override
    void close();
}
