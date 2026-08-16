package io.github.tlaplus.hardening.workflow;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Diagnostic formatting shared by isolated tool entry points. */
final class WorkerDiagnostics {
    private WorkerDiagnostics() {}

    static String stackTrace(Throwable exception) {
        var output = new StringWriter();
        exception.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    static String append(String diagnostic, String addition) {
        if (diagnostic.isBlank()) {
            return addition;
        }
        if (diagnostic.endsWith("\n")) {
            return diagnostic + addition;
        }
        return diagnostic + System.lineSeparator() + addition;
    }
}
