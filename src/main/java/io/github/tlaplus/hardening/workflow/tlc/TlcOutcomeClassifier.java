package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import tlc2.output.EC;

/**
 * Maps TLC's internal error constants and exit statuses to workflow outcomes, for TLC and for a
 * generic checker that follows TLC's exit statuses (ADR 0033 §2).
 */
@ArchRole(Role.POLICY)
@Key
public final class TlcOutcomeClassifier {
    /**
     * What TLC's command line prints for {@code EC.TLC_INTEGER_TOO_BIG}, which it reports with the
     * general exit status 255.
     */
    static final String INTEGER_TOO_BIG = "TLC can't handle a number this big.";

    private TlcOutcomeClassifier() {}

    static ToolResult classifyErrorCode(int errorCode, String diagnostic) {
        if (errorCode == EC.TLC_INTEGER_TOO_BIG) {
            return ToolResult.failure(CheckerFailureCode.SPEC_EVAL, diagnostic);
        }
        return classifyExitStatus(
                EC.ExitStatus.errorConstantToExitStatus(errorCode), diagnostic);
    }

    /**
     * Classifies the exit status of a command line that follows TLC's. Integer overflow has the
     * general status there, so it is recognized by its message and classified as an evaluation
     * failure, as {@link #classifyErrorCode} does in process (ADR 0033, probe P2).
     */
    public static ToolResult classifyCommandLine(int exitStatus, String diagnostic) {
        if (exitStatus == EC.ExitStatus.ERROR && diagnostic.contains(INTEGER_TOO_BIG)) {
            return ToolResult.failure(CheckerFailureCode.SPEC_EVAL, diagnostic);
        }
        return classifyExitStatus(exitStatus, diagnostic);
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
            return ToolResult.counterexample(diagnostic);
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
