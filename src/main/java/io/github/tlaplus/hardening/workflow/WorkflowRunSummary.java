package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import io.github.tlaplus.hardening.workflow.parser.ParserStageSummary;
import io.github.tlaplus.hardening.workflow.checker.CheckerStageSummary;
import java.time.Duration;
import java.util.Objects;

/** Cumulative corpus statistics and stopping reason after one complete workflow invocation. */
public record WorkflowRunSummary(
        StopReason stopReason,
        PbtStageSummary generator,
        ParserStageSummary parser,
        CheckerStageSummary tlc,
        CheckerStageSummary apalache,
        CorpusInventory corpus,
        Duration totalElapsed) {
    public WorkflowRunSummary {
        Objects.requireNonNull(stopReason, "stopReason");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(tlc, "tlc");
        Objects.requireNonNull(apalache, "apalache");
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(totalElapsed, "totalElapsed");
        if (totalElapsed.isNegative()) {
            throw new IllegalArgumentException("total elapsed time must be nonnegative");
        }
    }

    public enum StopReason {
        COMPLETED,
        CAPACITY_REACHED
    }
}
