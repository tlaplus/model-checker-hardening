package io.github.tlaplus.hardening.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/** Human-readable diagnostic formatting for failures. */
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

    /** Appends {@code addition} to {@code diagnostic} on a line of its own. */
    public static String append(String diagnostic, String addition) {
        if (diagnostic.isBlank()) {
            return addition;
        }
        if (diagnostic.endsWith("\n")) {
            return diagnostic + addition;
        }
        return diagnostic + System.lineSeparator() + addition;
    }

    /** Returns the complete printable stack trace of a failure. */
    public static String stackTrace(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        var output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
