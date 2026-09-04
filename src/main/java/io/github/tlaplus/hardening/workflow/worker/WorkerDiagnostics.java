package io.github.tlaplus.hardening.workflow.worker;

/** Diagnostic formatting shared by isolated tool entry points. */
public final class WorkerDiagnostics {
    private WorkerDiagnostics() {}

    public static String append(String diagnostic, String addition) {
        if (diagnostic.isBlank()) {
            return addition;
        }
        if (diagnostic.endsWith("\n")) {
            return diagnostic + addition;
        }
        return diagnostic + System.lineSeparator() + addition;
    }
}
