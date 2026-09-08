package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.PrintWriter;
import java.nio.file.Path;

/** Formats command failures without coupling diagnostics to picocli. */
final class CommandDiagnostic {
    private CommandDiagnostic() {}

    static void print(PrintWriter output, String operation, Path path, Throwable failure) {
        output.printf("fuzztla: %s '%s': %s%n", operation, path, Diagnostics.message(failure));
    }
}
