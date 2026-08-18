package io.github.tlaplus.hardening.workflow.checker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.Optional;

/** Checker-specific behavior injected into the shared checker stage. */
public interface CheckerBackend {
    String name();

    String displayName();

    int maximumEntries();

    int workerCount();

    int cpuPermits();

    ToolResult check(String source) throws WorkflowException, InterruptedException;

    Optional<String> failureDetail(String diagnostic);
}
