package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
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
        if (failureCode.isPresent() && outcome != StageOutcome.FAIL) {
            throw new IllegalArgumentException("only a failed tool result may carry a failure code");
        }
    }

    public ToolResult(StageOutcome outcome, String diagnostic) {
        this(outcome, Optional.empty(), diagnostic);
    }

    public static ToolResult failure(CheckerFailureCode failureCode, String diagnostic) {
        return new ToolResult(
                StageOutcome.FAIL,
                Optional.of(Objects.requireNonNull(failureCode, "failureCode")),
                diagnostic);
    }
}
