package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import io.github.tlaplus.hardening.workflow.parser.ParserStageSummary;
import io.github.tlaplus.hardening.workflow.tlc.TlcStageSummary;
import java.util.Objects;

/** Final counters and stopping reason for one complete workflow invocation. */
public record WorkflowRunSummary(
        StopReason stopReason,
        PbtStageSummary generator,
        ParserStageSummary parser,
        TlcStageSummary tlc,
        CorpusInventory corpus) {
    public WorkflowRunSummary {
        Objects.requireNonNull(stopReason, "stopReason");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(tlc, "tlc");
        Objects.requireNonNull(corpus, "corpus");
    }

    public enum StopReason {
        COMPLETED,
        CAPACITY_REACHED
    }
}
