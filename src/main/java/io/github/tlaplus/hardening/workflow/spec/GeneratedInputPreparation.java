package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/** Regenerates and renders one tool input while preserving generator-crash artifacts. */
public final class GeneratedInputPreparation {
    private final String stage;
    private final CorpusDirectory corpus;
    private final SpecDecoders decoders;
    private final Function<TlaModule, String> renderer;

    public GeneratedInputPreparation(
            String stage,
            CorpusDirectory corpus,
            SpecDecoders decoders,
            Function<TlaModule, String> renderer) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.decoders = Objects.requireNonNull(decoders, "decoders");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * Decodes one stored entry through the decoder its kind names.
     *
     * @throws WorkflowException if generation failed; a generation failure also preserves the
     *     exact bytes as a crash artifact
     */
    public ToolInput prepare(Path path, CorpusInput input) throws WorkflowException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(input, "input");
        try {
            var artifact = decoders.decode(input);
            return new ToolInput(renderer.apply(artifact.module()), artifact.length());
        } catch (RuntimeException | StackOverflowError failure) {
            throw new WorkflowException(recordCrash(path, input, failure), failure);
        }
    }

    private String recordCrash(Path path, CorpusInput input, Throwable failure) {
        var message = "cannot prepare "
                + stage
                + " specification from corpus entry '"
                + path
                + "': "
                + Diagnostics.message(failure);
        return corpus.preserveGeneratorCrash(input.kind(), input.input(), failure)
                .appendTo(message);
    }
}
