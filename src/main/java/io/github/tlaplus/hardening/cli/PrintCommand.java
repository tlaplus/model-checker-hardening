package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import io.github.tlaplus.hardening.gen.IrGenerators;
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

    @Parameters(index = "0", paramLabel = "FILE", description = "Binary generator input.")
    private Path input;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(input);
        } catch (IOException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot read '%s': %s%n",
                            input, diagnostic(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }

        try {
            var expression = IrGenerators.expressions().generate(bytes);
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
                            input, diagnostic(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private static String diagnostic(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
