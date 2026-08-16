package io.github.tlaplus.hardening.workflow;

import java.util.Objects;

/** One request result returned by an isolated tool worker. */
record ToolResult(StageOutcome outcome, String diagnostic) {
    ToolResult {
        Objects.requireNonNull(outcome, "outcome");
        diagnostic = Objects.requireNonNullElse(diagnostic, "");
    }
}
