package io.github.tlaplus.hardening.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "report",
        description = "Show how to write a campaign report of a corpus.")
final class ReportCommand implements Callable<Integer> {
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(
            names = "--corpus",
            defaultValue = "corpus",
            paramLabel = "DIR",
            description = "Corpus directory (default: ${DEFAULT-VALUE}).")
    private Path corpus;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        var out = spec.commandLine().getOut();
        out.print(ReportInstructions.render(ProjectDirectory.locate(), corpus));
        out.flush();
        return CommandLine.ExitCode.OK;
    }
}
