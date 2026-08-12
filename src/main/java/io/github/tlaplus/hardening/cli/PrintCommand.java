package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.pbt.CorpusDirectory;
import io.github.tlaplus.hardening.pbt.CorpusException;
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

@Command(name = "print", description = "Generate and print a TLA+ expression from a binary file.")
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

    @Parameters(index = "0", paramLabel = "FILE", description = "Binary generator input.")
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

        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(input);
        } catch (IOException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot read '%s': %s%n",
                            input, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }

        try {
            var expression = generator.generate(bytes);
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
