package io.github.tlaplus.hardening.cli;

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
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.LibraryManifest;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
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
        final FuzzTlaConfig config;
        final CorpusDirectory corpusDirectory;
        if (corpus == null) {
            config = FuzzTlaConfig.defaults();
            corpusDirectory = null;
        } else {
            try {
                corpusDirectory = CorpusDirectory.openExisting(corpus);
                config = TomlConfig.read(corpusDirectory.resolve(CorpusPath.CONFIG));
            } catch (IOException | ConfigException | CorpusException exception) {
                CommandDiagnostic.print(spec.commandLine().getErr(), "cannot read corpus", corpus, exception);
                return CommandLine.ExitCode.SOFTWARE;
            }
        }

        final byte[] encoded;
        try {
            encoded = Files.readAllBytes(input);
        } catch (IOException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot read", input, exception);
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
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot decode", input, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
        try (var shutdown = RunShutdownHook.install()) {
            var decoders = SpecDecoders.prepare(config);
            if (corpusDirectory != null) {
                LibraryManifest.verify(
                        corpusDirectory, decoders.libraryManifest(), false);
            }
            var artifact = decoders.decode(corpusInput);
            var rendered = DecodedInputRenderer.render(artifact, renderMode());
            // Every rendering can be reported inside its envelope, so the wrapping is decided
            // once here rather than in each branch.
            print(envelope == null ? rendered : EnvelopeReport.render(envelope, rendered));
            return CommandLine.ExitCode.OK;
        } catch (IOException | CorpusException | WorkflowException | RuntimeException | StackOverflowError exception) {
            CommandDiagnostic.print(
                    spec.commandLine().getErr(), "cannot generate a specification from", input, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private DecodedInputRenderer.Mode renderMode() {
        if (outputMode == null || outputMode.envelope) {
            return DecodedInputRenderer.Mode.DEFAULT;
        }
        return outputMode.apalacheIr
                ? DecodedInputRenderer.Mode.APALACHE_IR
                : DecodedInputRenderer.Mode.SPECIFICATION;
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
