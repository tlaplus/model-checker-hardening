package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.util.Objects;

/**
 * The collaborators every stage of one workflow invocation shares: the corpus it reads and writes,
 * the decoder each stored input kind is regenerated through, the CPU budget it competes for, the
 * stop state it observes and reports to, and whether its checkers record their counterexamples for
 * replay (ADR 0034 §5).
 */
@ArchRole(Role.MODEL)
@Key
public record StageEnvironment(
        CorpusDirectory corpus,
        SpecDecoders decoders,
        CpuBudget cpuBudget,
        WorkflowControl control,
        WorkflowEvents events,
        boolean recordsTraces) {
    public StageEnvironment {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(decoders, "decoders");
        Objects.requireNonNull(cpuBudget, "cpuBudget");
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(events, "events");
    }

    /** An environment whose checkers record no traces. */
    public StageEnvironment(
            CorpusDirectory corpus,
            SpecDecoders decoders,
            CpuBudget cpuBudget,
            WorkflowControl control,
            WorkflowEvents events) {
        this(corpus, decoders, cpuBudget, control, events, false);
    }
}
