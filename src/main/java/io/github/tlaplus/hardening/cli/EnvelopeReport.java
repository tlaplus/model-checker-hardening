package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
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

    /** Renders the supported envelope fields above the already-rendered input. */
    static String render(CorpusEnvelope envelope, String renderedInput) {
        var newline = System.lineSeparator();
        var output = new StringBuilder();
        output.append("kind: ")
                .append(envelope.corpusInput().kind().encodedName())
                .append(newline);
        envelope.generation().ifPresent(generation -> {
            output.append("gen:").append(newline);
            output.append("  cohort: ").append(generation.cohort()).append(newline);
            output.append("  richness: ").append(generation.richness()).append(newline);
        });
        if (!envelope.stages().isEmpty()) {
            output.append("stages:").append(newline);
            for (var stage : envelope.stages()) {
                output.append("  ").append(stage.stage()).append(":").append(newline);
                output.append("    verdict: ")
                    .append(stage.verdict().encodedName())
                    .append(newline);
                stage.failure().ifPresent(failure -> {
                    output.append("    code: ")
                            .append(failure.code().encodedCode())
                            .append(" (")
                            .append(failure.code().symbol())
                            .append(")")
                            .append(newline);
                    failure.detail().ifPresent(detail -> output.append("    detail: ")
                            .append(detail)
                            .append(newline));
                });
                output.append("    startTime: ").append(stage.startTime()).append(newline);
                output.append("    endTime: ")
                        .append(stage.endTime())
                        .append(" (duration: ")
                        .append(HumanDuration.format(Duration.between(
                                stage.startTime(), stage.endTime())))
                        .append(")")
                        .append(newline);
            }
        }
        output.append("input:").append(newline);
        renderedInput.lines().forEach(line -> output.append("  ").append(line).append(newline));
        return output.toString();
    }
}
