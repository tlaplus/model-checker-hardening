package io.github.tlaplus.hardening.cli;

/** Shared formatting for concise command-line failure messages. */
final class CliDiagnostics {
    private CliDiagnostics() {}

    static String message(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
