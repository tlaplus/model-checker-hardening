package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.WorkerDiagnostics;

/** Maps Apalache tool exit statuses to shared workflow outcomes. */
final class ApalacheOutcomeClassifier {
    private ApalacheOutcomeClassifier() {}

    static ToolResult classify(int exitStatus, String diagnostic) {
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
}
