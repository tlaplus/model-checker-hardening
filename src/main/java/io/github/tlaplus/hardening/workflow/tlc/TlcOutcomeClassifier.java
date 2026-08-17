package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import tlc2.output.EC;

/** Maps TLC's internal error constants to workflow outcomes. */
final class TlcOutcomeClassifier {
    private TlcOutcomeClassifier() {}

    static ToolResult classifyErrorCode(int errorCode, String diagnostic) {
        if (errorCode == EC.TLC_INTEGER_TOO_BIG) {
            return ToolResult.failure(CheckerFailureCode.SPEC_EVAL, diagnostic);
        }
        return classifyExitStatus(
                EC.ExitStatus.errorConstantToExitStatus(errorCode), diagnostic);
    }

    static ToolResult classifyExitStatus(int exitStatus, String diagnostic) {
        if (exitStatus == EC.ExitStatus.SUCCESS) {
            return new ToolResult(StageOutcome.PASS, diagnostic);
        }
        if (exitStatus == EC.ExitStatus.VIOLATION_ASSUMPTION
                || exitStatus == EC.ExitStatus.VIOLATION_DEADLOCK
                || exitStatus == EC.ExitStatus.VIOLATION_SAFETY
                || exitStatus == EC.ExitStatus.VIOLATION_LIVENESS
                || exitStatus == EC.ExitStatus.VIOLATION_ASSERT) {
            return ToolResult.failure(CheckerFailureCode.COUNTEREXAMPLE, diagnostic);
        }
        if (exitStatus == EC.ExitStatus.FAILURE_SPEC_EVAL
                || exitStatus == EC.ExitStatus.FAILURE_SAFETY_EVAL
                || exitStatus == EC.ExitStatus.FAILURE_LIVENESS_EVAL) {
            return ToolResult.failure(CheckerFailureCode.SPEC_EVAL, diagnostic);
        }
        if (exitStatus == EC.ExitStatus.ERROR_SPEC_PARSE
                || exitStatus == EC.ExitStatus.ERROR_CONFIG_PARSE) {
            return ToolResult.failure(CheckerFailureCode.PARSE, diagnostic);
        }
        return new ToolResult(StageOutcome.CRASH, diagnostic);
    }
}
