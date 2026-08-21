package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.WorkerDiagnostics;
import java.util.List;

/** Maps Apalache tool exit statuses to shared workflow outcomes. */
final class ApalacheOutcomeClassifier {
    private static final String INPUT_ERROR_PREFIX = "Input error (see the manual): ";
    private static final List<String> UNDEFINED_ARITHMETIC = List.of(
            "Division by zero", "Mod by zero", "0 ^ 0 is undefined");

    private ApalacheOutcomeClassifier() {}

    static ToolResult classify(int exitStatus, String diagnostic) {
        if (exitStatus == 255 && isUndefinedArithmetic(diagnostic)) {
            return ToolResult.failure(CheckerFailureCode.SPEC_EVAL, diagnostic);
        }
        return switch (exitStatus) {
            case 0 -> new ToolResult(StageOutcome.PASS, diagnostic);
            case 12 -> ToolResult.failure(CheckerFailureCode.COUNTEREXAMPLE, diagnostic);
            case 75 -> ToolResult.failure(CheckerFailureCode.SPEC_EVAL, diagnostic);
            case 120 -> ToolResult.failure(CheckerFailureCode.TYPECHECK, diagnostic);
            case 150 -> ToolResult.failure(CheckerFailureCode.PARSE, diagnostic);
            default -> new ToolResult(
                    StageOutcome.CRASH,
                    WorkerDiagnostics.append(
                            "Apalache exited with status " + exitStatus, diagnostic));
        };
    }

    private static boolean isUndefinedArithmetic(String diagnostic) {
        return diagnostic.lines()
                .map(String::strip)
                .anyMatch(line -> UNDEFINED_ARITHMETIC.stream()
                        .anyMatch(message -> matchesInputError(line, message)));
    }

    private static boolean matchesInputError(String line, String message) {
        var expected = INPUT_ERROR_PREFIX + message;
        return line.equals(expected) || line.startsWith(expected + " ");
    }
}
