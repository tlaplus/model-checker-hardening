package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

/**
 * Renders a decoded corpus envelope and its expression as plain text.
 *
 * <p>Only the fields this build models are printed; an envelope may carry more. The stage section
 * is omitted entirely when no stage has run, so an entry still in {@code 00-inputs} reports its
 * kind and input alone.
 */
final class EnvelopeReport {
    private static final int EXPRESSION_WIDTH = 80;
    private static final int EXPRESSION_INDENT = 2;

    private EnvelopeReport() {}

    /** Renders one expression as TLA+, with no surrounding envelope fields. */
    static String expression(TlaEx expression) {
        var buffer = new StringWriter();
        var printWriter = new PrintWriter(buffer);
        var writer = new PrettyWriter(
                printWriter,
                new TextLayout(EXPRESSION_WIDTH, EXPRESSION_INDENT),
                new TlaDeclAnnotator());
        writer.write(expression);
        printWriter.flush();
        return buffer.toString();
    }

    /**
     * Renders the supported envelope fields above the already-rendered input.
     *
     * <p>Every value is passed as a {@code printf} argument rather than concatenated into the
     * format string: a stage name comes from the document and may contain a {@code %}.
     */
    static String render(CorpusEnvelope envelope, String renderedInput) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("kind: %s%n", envelope.corpusInput().kind().encodedName());
            envelope.generation().ifPresent(generation -> {
                writer.printf("gen:%n");
                writer.printf("  cohort: %d%n", generation.cohort());
                writer.printf("  richness: %s%n", generation.richness());
            });
            if (!envelope.stages().isEmpty()) {
                writer.printf("stages:%n");
                envelope.stages().forEach(stage -> printStage(writer, stage));
            }
            writer.printf("input:%n");
            renderedInput.lines().forEach(line -> writer.printf("  %s%n", line));
        }
        return output.toString();
    }

    private static void printStage(PrintWriter writer, StageMetadata stage) {
        writer.printf("  %s:%n", stage.stage());
        writer.printf("    verdict: %s%n", stage.verdict().encodedName());
        stage.failure().ifPresent(failure -> {
            writer.printf(
                    "    code: %d (%s)%n",
                    failure.code().encodedCode(), failure.code().symbol());
            failure.detail().ifPresent(detail -> writer.printf("    detail: %s%n", detail));
        });
        writer.printf("    startTime: %s%n", stage.startTime());
        writer.printf(
                "    endTime: %s (duration: %s)%n",
                stage.endTime(),
                HumanDuration.format(Duration.between(stage.startTime(), stage.endTime())));
    }
}
