package io.github.tlaplus.hardening.workflow.execution;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.Objects;

/**
 * The collaborators every stage of one workflow invocation shares: the corpus it reads and writes,
 * the expression generator that decodes stored inputs, the CPU budget it competes for, and the stop
 * state it observes and reports to.
 */
public record StageEnvironment(
        CorpusDirectory corpus,
        Generator<TlaEx> generator,
        CpuBudget cpuBudget,
        WorkflowControl control) {
    public StageEnvironment {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(cpuBudget, "cpuBudget");
        Objects.requireNonNull(control, "control");
    }
}
