package io.github.tlaplus.hardening.workflow.worker;

import java.util.Objects;

/** One request result returned by an isolated tool worker. */
public record ToolResult(StageOutcome outcome, String diagnostic) {
    public ToolResult {
        Objects.requireNonNull(outcome, "outcome");
        diagnostic = Objects.requireNonNullElse(diagnostic, "");
    }
}
