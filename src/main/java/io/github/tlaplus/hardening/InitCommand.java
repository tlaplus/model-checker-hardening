package io.github.tlaplus.hardening;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "init", description = "Initialize a FuzzTLA project.")
final class InitCommand implements Callable<Integer> {
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().getErr().println("fuzztla: init is not implemented yet");
        return CommandLine.ExitCode.SOFTWARE;
    }
}
