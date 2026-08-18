package io.github.tlaplus.hardening.workflow.checker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.util.Optional;

/** Checker-specific behavior injected into the shared checker stage. */
public interface CheckerBackend {
    String name();

    String displayName();

    int maximumEntries();

    int workerCount();

    int cpuPermits();

    CheckerWorker startWorker() throws WorkflowException, InterruptedException;

    Optional<String> failureDetail(String diagnostic);
}
