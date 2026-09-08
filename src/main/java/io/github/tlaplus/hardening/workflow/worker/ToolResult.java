package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Objects;
import java.util.Optional;

/** One request result returned by an isolated tool worker. */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record ToolResult(
        StageOutcome outcome,
        Optional<CheckerFailureCode> failureCode,
        String diagnostic) {
    public ToolResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(failureCode, "failureCode");
        diagnostic = Objects.requireNonNullElse(diagnostic, "");
        Preconditions.require(failureCode.isEmpty() || outcome == StageOutcome.FAIL,
                "only a failed tool result may carry a failure code");
    }

    public ToolResult(StageOutcome outcome, String diagnostic) {
        this(outcome, Optional.empty(), diagnostic);
    }

    /** Keeps captured tool output before the full stack trace of an escaped failure. */
    public static ToolResult crash(Throwable failure, String diagnostic) {
        return new ToolResult(StageOutcome.CRASH,
                WorkerDiagnostics.append(diagnostic, Diagnostics.stackTrace(failure)));
    }

    public static ToolResult counterexample(String diagnostic) {
        return new ToolResult(StageOutcome.COUNTEREXAMPLE, diagnostic);
    }

    public static ToolResult failure(CheckerFailureCode failureCode, String diagnostic) {
        return new ToolResult(
                StageOutcome.FAIL,
                Optional.of(Objects.requireNonNull(failureCode, "failureCode")),
                diagnostic);
    }
}
