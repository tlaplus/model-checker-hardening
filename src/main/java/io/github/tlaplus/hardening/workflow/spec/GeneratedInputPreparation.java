package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/** Generates and renders one tool input while preserving generator-crash artifacts. */
public final class GeneratedInputPreparation {
    private final String stage;
    private final CorpusDirectory corpus;
    private final Generator<TlaEx> generator;
    private final Function<TlaEx, String> renderer;

    public GeneratedInputPreparation(
            String stage,
            CorpusDirectory corpus,
            Generator<TlaEx> generator,
            Function<TlaEx, String> renderer) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public String prepare(Path path, byte[] input) throws WorkflowException {
        try {
            return renderer.apply(generator.generate(input));
        } catch (RuntimeException | StackOverflowError failure) {
            var message = "cannot prepare "
                    + stage
                    + " specification from corpus entry '"
                    + path
                    + "': "
                    + Diagnostics.message(failure);
            try {
                var candidate = corpus.recordGeneratorCrash(input, failure);
                message += "; crash saved to '" + candidate + "'";
            } catch (IOException | CorpusException | RuntimeException recordingFailure) {
                failure.addSuppressed(recordingFailure);
                message += "; crash artifact could not be saved: "
                        + Diagnostics.message(recordingFailure);
            }
            throw new WorkflowException(message, failure);
        }
    }
}
