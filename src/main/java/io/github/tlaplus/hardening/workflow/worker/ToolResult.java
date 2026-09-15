package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Objects;
import java.util.Optional;

/**
 * One request result returned by an isolated tool worker.
 *
 * <p>A model checker that measures its exploration attaches the metrics with {@link #withMetrics};
 * a crash carries none, since it did not finish what it measured.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record ToolResult(
        StageOutcome outcome,
        Optional<CheckerFailureCode> failureCode,
        String diagnostic,
        Optional<ExplorationMetrics> metrics) {
    public ToolResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(failureCode, "failureCode");
        diagnostic = Objects.requireNonNullElse(diagnostic, "");
        Objects.requireNonNull(metrics, "metrics");
        Preconditions.require(failureCode.isEmpty() || outcome == StageOutcome.FAIL,
                "only a failed tool result may carry a failure code");
        Preconditions.require(metrics.isEmpty() || outcome != StageOutcome.CRASH,
                "a crashed tool result carries no exploration metrics");
    }

    public ToolResult(
            StageOutcome outcome, Optional<CheckerFailureCode> failureCode, String diagnostic) {
        this(outcome, failureCode, diagnostic, Optional.empty());
    }

    public ToolResult(StageOutcome outcome, String diagnostic) {
        this(outcome, Optional.empty(), diagnostic);
    }

    /** Returns this result with {@code value} as its exploration metrics. */
    public ToolResult withMetrics(ExplorationMetrics value) {
        return new ToolResult(
                outcome, failureCode, diagnostic, Optional.of(Objects.requireNonNull(value, "metrics")));
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
