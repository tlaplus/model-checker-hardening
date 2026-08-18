package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import io.github.tlaplus.hardening.workflow.parser.ParserStageSummary;
import io.github.tlaplus.hardening.workflow.checker.CheckerStageSummary;
import java.time.Duration;
import java.util.Objects;

/** Best-effort snapshot of cumulative corpus statistics and current pending work. */
public record WorkflowProgress(
        Phase phase,
        PbtStageSummary generator,
        ParserStageSummary parser,
        CheckerStageSummary tlc,
        CheckerStageSummary apalache,
        long corpusEntries,
        long awaitingParser,
        long awaitingTlc,
        long awaitingApalache,
        Duration totalElapsed) {
    public WorkflowProgress {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(tlc, "tlc");
        Objects.requireNonNull(apalache, "apalache");
        Objects.requireNonNull(totalElapsed, "totalElapsed");
        if (corpusEntries < 0
                || awaitingParser < 0
                || awaitingTlc < 0
                || awaitingApalache < 0) {
            throw new IllegalArgumentException("workflow progress counters must be nonnegative");
        }
        if (awaitingParser > corpusEntries
                || awaitingTlc > corpusEntries
                || awaitingApalache > corpusEntries) {
            throw new IllegalArgumentException(
                    "pending stage counts must not exceed corpus entries");
        }
        if (totalElapsed.isNegative()) {
            throw new IllegalArgumentException("total elapsed time must be nonnegative");
        }
    }

    /** The externally visible phase of a workflow invocation. */
    public enum Phase {
        RUNNING,
        FINALIZING
    }
}
