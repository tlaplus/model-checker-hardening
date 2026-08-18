package io.github.tlaplus.hardening.common;

/** Concise, human-readable descriptions of failures. */
public final class Diagnostics {
    private Diagnostics() {}

    /**
     * Returns an exception's message, or its simple class name when the message carries no
     * information. A null exception is reported as an unknown failure.
     */
    public static String message(Throwable exception) {
        if (exception == null) {
            return "unknown failure";
        }
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
