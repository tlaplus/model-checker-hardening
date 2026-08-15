package io.github.tlaplus.hardening.workflow;

import java.util.Objects;

/** Best-effort in-memory snapshot of a running workflow. */
public record WorkflowProgress(
        PbtStageSummary generator,
        ParserStageSummary parser,
        long corpusEntries,
        long remainingInputs) {
    public WorkflowProgress {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(parser, "parser");
        if (corpusEntries < 0 || remainingInputs < 0) {
            throw new IllegalArgumentException("workflow progress counters must be nonnegative");
        }
        if (remainingInputs > corpusEntries) {
            throw new IllegalArgumentException(
                    "remaining inputs must not exceed corpus entries");
        }
    }
}
