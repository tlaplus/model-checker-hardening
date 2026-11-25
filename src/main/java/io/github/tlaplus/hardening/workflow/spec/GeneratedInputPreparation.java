package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apalache_mc.tla.jir.TlaModules;

/** Regenerates and renders one tool input while preserving generator-crash artifacts. */
public final class GeneratedInputPreparation {
    private final String stage;
    private final CorpusDirectory corpus;
    private final SpecDecoders decoders;
    private final Function<SpecArtifact, String> renderer;
    private final boolean recordsTraces;

    /**
     * One tool input, and the state variables of its module in declaration order, by which a
     * recorded trace is read (ADR 0034 §5).
     */
    public record Prepared(ToolInput input, List<TlaVarDecl> variables) {
        public Prepared {
            Objects.requireNonNull(input, "input");
            variables = List.copyOf(variables);
        }
    }

    /**
     * @param recordsTraces whether a checker is asked for the trace of a counterexample of a
     *     generated input (ADR 0034 §5); a derived input's own trace is never replayed
     */
    public GeneratedInputPreparation(
            String stage,
            CorpusDirectory corpus,
            SpecDecoders decoders,
            Function<SpecArtifact, String> renderer,
            boolean recordsTraces) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.decoders = Objects.requireNonNull(decoders, "decoders");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.recordsTraces = recordsTraces;
    }

    /**
     * Decodes one stored entry through the decoder its kind names.
     *
     * @throws WorkflowException if generation failed; a generation failure also preserves the
     *     exact bytes as a crash artifact
     */
    public Prepared prepare(Path path, CorpusInput input) throws WorkflowException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(input, "input");
        try {
            var artifact = decoders.decode(input);
            var request = artifact.request().withTraceRecording(recordsTraces && !input.kind().derived());
            var variables = TlaModules.declarations(artifact.module()).stream()
                    .filter(TlaVarDecl.class::isInstance).map(TlaVarDecl.class::cast).toList();
            return new Prepared(new ToolInput(renderer.apply(artifact), request), variables);
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
