package io.github.tlaplus.hardening.workflow.checker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.util.Optional;

/**
 * Checker-specific behavior injected into the shared checker stage.
 *
 * <p>An implementation chooses one of two worker lifecycles, and the stage supports both:
 *
 * <ul>
 *   <li><em>One child process per input.</em> {@link #startWorker()} returns a worker that runs a
 *       single check, so process state cannot leak between inputs at the cost of one JVM startup
 *       each. TLC works this way.
 *   <li><em>One child process per worker.</em> {@link #startWorker()} returns a worker that serves
 *       many inputs sequentially until a crash retires it, amortizing JVM startup. Apalache works
 *       this way.
 * </ul>
 *
 * <p>The stage calls {@link #startWorker()} again after every crash verdict, so a one-shot worker
 * simply reports a crash on every failure and a persistent one is replaced only when it dies.
 */
public interface CheckerBackend {
    String name();

    String displayName();

    int maximumEntries();

    int workerCount();

    int cpuPermits();

    /** Returns a worker for the next input, per the lifecycle documented on this interface. */
    CheckerWorker startWorker() throws WorkflowException, InterruptedException;

    Optional<String> failureDetail(String diagnostic);
}
