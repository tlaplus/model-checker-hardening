package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusInputFormatException;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.FuzzInputModule;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "print",
        description =
                "Print a TLA+ expression, parser specification, or decoded corpus envelope.")
final class PrintCommand implements Callable<Integer> {
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(
            names = "--corpus",
            paramLabel = "DIR",
            description = "Use generator settings from this corpus.")
    private Path corpus;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    private OutputMode outputMode;

    @Parameters(index = "0", paramLabel = "FILE", description = "CBOR corpus input.")
    private Path input;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        final Generator<TlaEx> generator;
        if (corpus == null) {
            generator = IrGenerators.expressions();
        } else {
            try {
                var config = CorpusDirectory.open(corpus).readConfig();
                generator = IrGenerators.expressions(config.generator());
            } catch (IOException | ConfigException | CorpusException exception) {
                spec.commandLine()
                        .getErr()
                        .printf(
                                "fuzztla: cannot read corpus '%s': %s%n",
                                corpus, CliDiagnostics.message(exception));
                return CommandLine.ExitCode.SOFTWARE;
            }
        }

        final byte[] encoded;
        try {
            encoded = Files.readAllBytes(input);
        } catch (IOException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot read '%s': %s%n",
                            input, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }

        final CorpusInput corpusInput;
        final CorpusEnvelope envelope;
        try {
            if (printsEnvelope()) {
                envelope = CorpusInputCodec.decodeEnvelope(encoded);
                corpusInput = envelope.corpusInput();
            } else {
                envelope = null;
                corpusInput = CorpusInputCodec.decode(encoded);
            }
        } catch (CorpusInputFormatException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot decode '%s': %s%n",
                            input, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
        if (corpusInput.kind() != CorpusInput.Kind.EXPRESSION) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot generate expression from '%s': unsupported input kind '%s'%n",
                            input, corpusInput.kind().encodedName());
            return CommandLine.ExitCode.SOFTWARE;
        }

        try {
            var expression = generator.generate(corpusInput.input());
            final String output;
            if (printsSpecification()) {
                var module = FuzzInputModule.create(expression);
                output = PrettyWriter.writeAsString(
                        module, TlaWriter$.MODULE$.STANDARD_MODULES());
            } else {
                var renderedExpression = renderExpression(expression);
                output = envelope == null
                        ? renderedExpression
                        : renderEnvelope(envelope, renderedExpression);
            }
            print(output);
            return CommandLine.ExitCode.OK;
        } catch (RuntimeException | StackOverflowError exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot generate expression from '%s': %s%n",
                            input, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private boolean printsSpecification() {
        return outputMode != null && outputMode.specification;
    }

    private boolean printsEnvelope() {
        return outputMode != null && outputMode.envelope;
    }

    private static String renderExpression(TlaEx expression) {
        var buffer = new StringWriter();
        var printWriter = new PrintWriter(buffer);
        var writer =
                new PrettyWriter(printWriter, new TextLayout(80, 2), new TlaDeclAnnotator());
        writer.write(expression);
        printWriter.flush();
        return buffer.toString();
    }

    private static String renderEnvelope(CorpusEnvelope envelope, String renderedInput) {
        var newline = System.lineSeparator();
        var output = new StringBuilder();
        output.append("kind: ")
                .append(envelope.corpusInput().kind().encodedName())
                .append(newline);
        if (!envelope.stages().isEmpty()) {
            output.append("stages:").append(newline);
            for (var stage : envelope.stages()) {
                output.append("  ").append(stage.stage()).append(":").append(newline);
                output.append("    verdict: ").append(stage.verdict()).append(newline);
                output.append("    startTime: ").append(stage.startTime()).append(newline);
                output.append("    endTime: ")
                        .append(stage.endTime())
                        .append(" (duration: ")
                        .append(formatDuration(Duration.between(
                                stage.startTime(), stage.endTime())))
                        .append(")")
                        .append(newline);
            }
        }
        output.append("input:").append(newline);
        renderedInput.lines().forEach(line -> output.append("  ").append(line).append(newline));
        return output.toString();
    }

    private static String formatDuration(Duration duration) {
        var output = new StringBuilder();
        appendDurationPart(output, duration.toDaysPart(), "d");
        appendDurationPart(output, duration.toHoursPart(), "h");
        appendDurationPart(output, duration.toMinutesPart(), "m");
        appendDurationPart(output, duration.toSecondsPart(), "s");
        return output.isEmpty() ? "0s" : output.toString();
    }

    private static void appendDurationPart(StringBuilder output, long value, String suffix) {
        if (value == 0) {
            return;
        }
        if (!output.isEmpty()) {
            output.append(' ');
        }
        output.append(value).append(suffix);
    }

    private void print(String output) {
        var writer = spec.commandLine().getOut();
        writer.print(output);
        if (!output.endsWith("\n")) {
            writer.println();
        }
        writer.flush();
    }

    private static final class OutputMode {
        @Option(
                names = "--spec",
                description = "Print the complete specification passed to the parser.")
        private boolean specification;

        @Option(
                names = "--envelope",
                description = "Print supported envelope fields with input rendered as TLA+.")
        private boolean envelope;
    }
}
