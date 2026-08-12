package io.github.tlaplus.hardening.gen;

/**
 * Indicates that the current input cannot be decoded into a valid value.
 *
 * <p>This exception represents an expected rejection of one input, not a defect in a generator.
 * Callers such as fuzz harnesses may catch it and continue with the next input. Other runtime
 * exceptions, especially checked-builder and invariant failures, should remain visible as bugs.
 */
public final class InputRejectedException extends RuntimeException {
    /**
     * Creates a rejection with a diagnostic message.
     *
     * @param message explanation of why generation cannot continue
     */
    public InputRejectedException(String message) {
        super(message);
    }

    /**
     * Creates a rejection caused by another expected generation failure.
     *
     * @param message explanation of why generation cannot continue
     * @param cause underlying expected failure
     */
    public InputRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
