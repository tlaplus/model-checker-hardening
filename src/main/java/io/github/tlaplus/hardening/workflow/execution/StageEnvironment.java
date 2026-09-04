package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import java.util.Map;
import java.util.Objects;

/**
 * The collaborators every stage of one workflow invocation shares: the corpus it reads and writes,
 * the decoder each stored input kind is regenerated through, the CPU budget it competes for, and
 * the stop state it observes and reports to.
 */
public record StageEnvironment(
        CorpusDirectory corpus,
        Map<InputKind, Generator<SpecArtifact>> decoders,
        CpuBudget cpuBudget,
        WorkflowControl control) {
    public StageEnvironment {
        Objects.requireNonNull(corpus, "corpus");
        decoders = Map.copyOf(Objects.requireNonNull(decoders, "decoders"));
        Objects.requireNonNull(cpuBudget, "cpuBudget");
        Objects.requireNonNull(control, "control");
    }
}
