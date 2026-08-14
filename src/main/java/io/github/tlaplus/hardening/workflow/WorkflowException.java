package io.github.tlaplus.hardening.workflow;

/** Reports an infrastructure or lifecycle failure in a fuzzing workflow. */
public final class WorkflowException extends Exception {
    public WorkflowException(String message) {
        super(message);
    }

    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
