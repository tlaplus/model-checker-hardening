package io.github.tlaplus.hardening.workflow;

import java.util.Objects;

/** Best-effort in-memory snapshot of an active workflow invocation. */
public record WorkflowProgress(
        Phase phase,
        PbtStageSummary generator,
        ParserStageSummary parser,
        TlcStageSummary tlc,
        long corpusEntries,
        long awaitingParser,
        long awaitingTlc,
        long pendingApalache) {
    public WorkflowProgress {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(tlc, "tlc");
        if (corpusEntries < 0
                || awaitingParser < 0
                || awaitingTlc < 0
                || pendingApalache < 0) {
            throw new IllegalArgumentException("workflow progress counters must be nonnegative");
        }
        if (awaitingParser > corpusEntries
                || awaitingTlc > corpusEntries
                || pendingApalache > corpusEntries) {
            throw new IllegalArgumentException(
                    "pending stage counts must not exceed corpus entries");
        }
    }

    /** The externally visible phase of a workflow invocation. */
    public enum Phase {
        RUNNING,
        FINALIZING
    }
}
