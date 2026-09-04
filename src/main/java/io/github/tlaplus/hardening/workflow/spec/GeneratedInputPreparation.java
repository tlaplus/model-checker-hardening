package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Regenerates and renders one tool input while preserving generator-crash artifacts. */
public final class GeneratedInputPreparation {
    private final String stage;
    private final CorpusDirectory corpus;
    private final Map<InputKind, Generator<SpecArtifact>> decoders;
    private final Function<TlaModule, String> renderer;

    public GeneratedInputPreparation(
            String stage,
            CorpusDirectory corpus,
            Map<InputKind, Generator<SpecArtifact>> decoders,
            Function<TlaModule, String> renderer) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.decoders = Map.copyOf(Objects.requireNonNull(decoders, "decoders"));
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * Decodes one stored entry through the decoder its kind names.
     *
     * @throws WorkflowException if no decoder serves that kind, or if generation failed; a
     *     generation failure also preserves the exact bytes as a crash artifact
     */
    public ToolInput prepare(Path path, CorpusInput input) throws WorkflowException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(input, "input");
        var decoder = decoders.get(input.kind());
        if (decoder == null) {
            throw new WorkflowException(
                    "the "
                            + stage
                            + " stage cannot decode corpus entry '"
                            + path
                            + "': no decoder for input kind '"
                            + input.kind().encodedName()
                            + "'");
        }
        try {
            var artifact = decoder.generate(input.input());
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
        try {
            var candidate = corpus.recordGeneratorCrash(
                    input.kind(), input.input(), failure);
            return message + "; crash saved to '" + candidate + "'";
        } catch (IOException | CorpusException | RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            return message
                    + "; crash artifact could not be saved: "
                    + Diagnostics.message(recordingFailure);
        }
    }
}
