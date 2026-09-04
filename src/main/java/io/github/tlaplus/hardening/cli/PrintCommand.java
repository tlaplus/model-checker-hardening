package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.tla.lir.TlaModule;
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
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
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
        final IrGenerationConfig generatorConfig;
        if (corpus == null) {
            generatorConfig = IrGenerationConfig.defaults();
        } else {
            try {
                var corpusDirectory = CorpusDirectory.openExisting(corpus);
                generatorConfig = TomlConfig.read(corpusDirectory.resolve(CorpusPath.CONFIG))
                        .generator();
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
        try {
            final String rendered;
            if (printsApalacheIr()) {
                rendered = ApalacheIrJson.render(module(generatorConfig, corpusInput));
            } else if (printsSpecification() || corpusInput.kind() != InputKind.EXPRESSION) {
                // Without a mode, an expression input prints its expression; a module has no
                // single expression to print, so it prints the module either way.
                rendered = SpecText.render(module(generatorConfig, corpusInput));
            } else {
                rendered = EnvelopeReport.expression(
                        IrGenerators.expressions(generatorConfig)
                                .generate(corpusInput.input()));
            }
            // Every rendering can be reported inside its envelope, so the wrapping is decided
            // once here rather than in each branch.
            print(envelope == null ? rendered : EnvelopeReport.render(envelope, rendered));
            return CommandLine.ExitCode.OK;
        } catch (RuntimeException | StackOverflowError exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot generate a specification from '%s': %s%n",
                            input, Diagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    /**
     * Regenerates the assembled module of one entry, through the decoder its kind names.
     *
     * <p>{@link SpecDecoders#of} covers every kind, so the lookup happens where the module is
     * wanted rather than ahead of the branch that wants it.
     */
    private static TlaModule module(IrGenerationConfig config, CorpusInput corpusInput) {
        return SpecDecoders.of(config)
                .get(corpusInput.kind())
                .generate(corpusInput.input())
                .module();
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
