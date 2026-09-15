package io.github.tlaplus.hardening.workflow.spec;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.signature.IrOperatorCounts;
import java.util.Objects;

/**
 * Replays a stored input and counts the operators of the code the checkers evaluate: the
 * definitions reachable from {@link FuzzInputModule#ENTRY_POINTS}.
 *
 * <p>Instances hold no mutable state and may be called from several threads, as the workflow
 * stages share one {@link SpecDecoders}.
 */
public final class EvaluatedOperators {
    private final SpecDecoders decoders;

    public EvaluatedOperators(SpecDecoders decoders) {
        this.decoders = Objects.requireNonNull(decoders, "decoders");
    }

    /**
     * Returns the operator counts of the module that {@code input} decodes to.
     *
     * @throws RuntimeException if the generator rejects or fails on the input
     * @throws StackOverflowError if the input nests too deeply to decode or walk
     */
    public IrOperatorCounts count(CorpusInput input) {
        Objects.requireNonNull(input, "input");
        return IrOperatorCounts.evaluated(
                decoders.decode(input).module(), FuzzInputModule.ENTRY_POINTS);
    }
}
