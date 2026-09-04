package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.util.Objects;

/**
 * The collaborators every stage of one workflow invocation shares: the corpus it reads and writes,
 * the decoder each stored input kind is regenerated through, the CPU budget it competes for, and
 * the stop state it observes and reports to.
 */
public record StageEnvironment(
        CorpusDirectory corpus,
        SpecDecoders decoders,
        CpuBudget cpuBudget,
        WorkflowControl control) {
    public StageEnvironment {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(decoders, "decoders");
        Objects.requireNonNull(cpuBudget, "cpuBudget");
        Objects.requireNonNull(control, "control");
    }
}
