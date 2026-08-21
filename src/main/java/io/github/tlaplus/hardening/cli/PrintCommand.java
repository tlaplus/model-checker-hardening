package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusFormatException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.workflow.spec.ExprInputToSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine;

@Command(
        name = "print",
        description =
                "Print a TLA+ expression, tool specification, or decoded corpus envelope.")
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
                var corpusDirectory = CorpusDirectory.openExisting(corpus);
                var config = TomlConfig.read(corpusDirectory.resolve(CorpusPath.CONFIG));
                generator = IrGenerators.expressions(config.generator());
            } catch (IOException | ConfigException | CorpusException exception) {
                spec.commandLine()
                        .getErr()
                        .printf(
                                "fuzztla: cannot read corpus '%s': %s%n",
                                corpus, Diagnostics.message(exception));
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
                            input, Diagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }

        final CorpusInput corpusInput;
        final CorpusEnvelope envelope;
        try {
            if (printsEnvelope()) {
                envelope = CorpusEnvelopeCodec.decodeEnvelope(encoded);
                corpusInput = envelope.corpusInput();
            } else {
                envelope = null;
                corpusInput = CorpusInputCodec.decode(encoded);
            }
        } catch (CorpusFormatException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot decode '%s': %s%n",
                            input, Diagnostics.message(exception));
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
            if (printsApalacheIr()) {
                output = ApalacheIrJson.render(expression);
            } else if (printsSpecification()) {
                output = ExprInputToSpec.render(expression);
            } else {
                var renderedExpression = EnvelopeReport.expression(expression);
                output = envelope == null
                        ? renderedExpression
                        : EnvelopeReport.render(envelope, renderedExpression);
            }
            print(output);
            return CommandLine.ExitCode.OK;
        } catch (RuntimeException | StackOverflowError exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot generate expression from '%s': %s%n",
                            input, Diagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private boolean printsSpecification() {
        return outputMode != null && outputMode.specification;
    }

    private boolean printsApalacheIr() {
        return outputMode != null && outputMode.apalacheIr;
    }

    private boolean printsEnvelope() {
        return outputMode != null && outputMode.envelope;
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
                names = "--apalache-ir",
                description = "Print the typed JSON specification passed to Apalache.")
        private boolean apalacheIr;

        @Option(
                names = "--spec",
                description = "Print the TLA+ specification passed to the parser and TLC.")
        private boolean specification;

        @Option(
                names = "--envelope",
                description = "Print supported envelope fields with input rendered as TLA+.")
        private boolean envelope;
    }
}
