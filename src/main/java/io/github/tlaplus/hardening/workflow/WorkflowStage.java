package io.github.tlaplus.hardening.workflow;

/** An independently scheduled workflow stage that owns its worker threads. */
public interface WorkflowStage extends AutoCloseable {
    String name();

    void start();

    void await() throws InterruptedException;

    @Override
    void close();
}
