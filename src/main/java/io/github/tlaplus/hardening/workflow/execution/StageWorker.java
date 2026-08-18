package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.util.Objects;

/** Runs one stage worker, turning its interruption or failure into shared stop state. */
public final class StageWorker {
    private StageWorker() {}

    /** The work one stage worker performs until its stage runs out of work or is stopped. */
    @FunctionalInterface
    public interface Body {
        void run() throws Exception;
    }

    /**
     * Runs a worker body to completion. An interruption while the workflow is still running, and
     * any failure that escapes the body, stop every stage; an interruption after the workflow has
     * already stopped is the expected shutdown path and is not reported.
     *
     * <p>{@code label} names the worker in the reported failure, for example {@code "parser
     * worker"}.
     */
    public static void run(WorkflowControl control, String label, Body body) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(body, "body");
        try {
            body.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!control.shouldStop()) {
                control.fail(new WorkflowException(label + " was interrupted", exception));
            }
        } catch (Exception | StackOverflowError exception) {
            control.fail(exception);
        }
    }
}
