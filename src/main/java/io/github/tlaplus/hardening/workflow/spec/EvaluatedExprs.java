package io.github.tlaplus.hardening.workflow.spec;

import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.signature.IrExprCounts;
import java.util.Objects;

/**
 * Replays a stored input and counts the expression constructs of the code the checkers evaluate: the
 * definitions reachable from {@link SpecArtifact#entryPoints()}.
 *
 * <p>Instances hold no mutable state and may be called from several threads, as the workflow
 * stages share one {@link SpecDecoders}.
 */
public final class EvaluatedExprs {
    private final SpecDecoders decoders;

    public EvaluatedExprs(SpecDecoders decoders) {
        this.decoders = Objects.requireNonNull(decoders, "decoders");
    }

    /**
     * Returns the construct counts of the module that {@code input} decodes to.
     *
     * @throws RuntimeException if the generator rejects or fails on the input
     * @throws StackOverflowError if the input nests too deeply to decode or walk
     */
    public ExprCounts count(CorpusInput input) {
        Objects.requireNonNull(input, "input");
        var artifact = decoders.decode(input);
        return IrExprCounts.evaluated(artifact.module(), artifact.entryPoints());
    }
}
