package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
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
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "print",
        description =
                "Generate and print a TLA+ expression or parser specification from a CBOR corpus input.")
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

    @Option(
            names = "--spec",
            description = "Print the complete specification passed to the parser.")
    private boolean printSpecification;

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
        try {
            corpusInput = CorpusInputCodec.decode(encoded);
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
            if (printSpecification) {
                var module = FuzzInputModule.create(expression);
                var source = PrettyWriter.writeAsString(
                        module, TlaWriter$.MODULE$.STANDARD_MODULES());
                spec.commandLine().getOut().print(source);
                if (!source.endsWith("\n")) {
                    spec.commandLine().getOut().println();
                }
                spec.commandLine().getOut().flush();
                return CommandLine.ExitCode.OK;
            }

            var buffer = new StringWriter();
            var printWriter = new PrintWriter(buffer);
            var writer = new PrettyWriter(
                    printWriter, new TextLayout(80, 2), new TlaDeclAnnotator());
            writer.write(expression);
            printWriter.flush();
            spec.commandLine().getOut().println(buffer);
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
}
