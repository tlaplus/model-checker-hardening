package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * One request result returned by an isolated tool worker.
 *
 * <p>A model checker that measures its exploration attaches the metrics with {@link #withMetrics};
 * a crash carries none, since it did not finish what it measured. A counterexample may carry its
 * trace, when the request asked for it ({@link #withTrace}).
 */
@ArchRole(Role.MODEL)
@Key
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record ToolResult(
        StageOutcome outcome,
        Optional<CheckerFailureCode> failureCode,
        String diagnostic,
        Optional<ExplorationMetrics> metrics,
        Optional<String> trace) {
    /**
     * The largest recorded trace a worker returns, in UTF-8 bytes; a longer one is dropped, which
     * changes no verdict (ADR 0034 §5).
     */
    public static final int MAXIMUM_TRACE_BYTES = 256 * 1024;

    public ToolResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(failureCode, "failureCode");
        diagnostic = Objects.requireNonNullElse(diagnostic, "");
        Objects.requireNonNull(metrics, "metrics");
        Preconditions.require(failureCode.isEmpty() || outcome == StageOutcome.FAIL,
                "only a failed tool result may carry a failure code");
        Preconditions.require(metrics.isEmpty() || outcome != StageOutcome.CRASH,
                "a crashed tool result carries no exploration metrics");
        Objects.requireNonNull(trace, "trace");
        Preconditions.require(trace.isEmpty() || outcome == StageOutcome.COUNTEREXAMPLE,
                "only a counterexample carries a trace");
    }

    public ToolResult(
            StageOutcome outcome,
            Optional<CheckerFailureCode> failureCode,
            String diagnostic,
            Optional<ExplorationMetrics> metrics) {
        this(outcome, failureCode, diagnostic, metrics, Optional.empty());
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
                outcome, failureCode, diagnostic, Optional.of(Objects.requireNonNull(value, "metrics")), trace);
    }

    /**
     * Returns this counterexample with its recorded trace, the JSON of ADR 0034 §5, or unchanged when
     * the trace exceeds {@link #MAXIMUM_TRACE_BYTES}.
     */
    public ToolResult withTrace(String value) {
        Objects.requireNonNull(value, "trace");
        if (value.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_TRACE_BYTES) {
            return this;
        }
        return new ToolResult(outcome, failureCode, diagnostic, metrics, Optional.of(value));
    }

    /** Keeps captured tool output before the full stack trace of an escaped failure. */
    public static ToolResult crash(Throwable failure, String diagnostic) {
        return new ToolResult(StageOutcome.CRASH,
                Diagnostics.append(diagnostic, Diagnostics.stackTrace(failure)));
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
