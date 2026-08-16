package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.gen.Generator;
import java.io.IOException;
import java.nio.file.Path;

/** Shared construction and rendering of the generated module consumed by tool stages. */
final class ExprInputToSpec {
    private ExprInputToSpec() {}

    static String render(
            String stage,
            Path path,
            byte[] input,
            CorpusDirectory corpus,
            Generator<TlaEx> generator)
            throws WorkflowException {
        try {
            var expression = generator.generate(input);
            return PrettyWriter.writeAsString(
                    FuzzInputModule.create(expression), TlaWriter$.MODULE$.STANDARD_MODULES());
        } catch (RuntimeException | StackOverflowError failure) {
            var message = "cannot prepare "
                    + stage
                    + " specification from corpus entry '"
                    + path
                    + "': "
                    + diagnostic(failure);
            try {
                var candidate = corpus.recordGeneratorCrash(input, failure);
                message += "; crash saved to '" + candidate + "'";
            } catch (IOException | CorpusException | RuntimeException recordingFailure) {
                failure.addSuppressed(recordingFailure);
                message += "; crash artifact could not be saved: " + diagnostic(recordingFailure);
            }
            throw new WorkflowException(message, failure);
        }
    }

    private static String diagnostic(Throwable failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
