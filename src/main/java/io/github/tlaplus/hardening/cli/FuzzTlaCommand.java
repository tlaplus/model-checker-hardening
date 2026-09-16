package io.github.tlaplus.hardening.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "fuzztla",
        description = "Synthesize TLA+ specifications to harden model checkers.",
        mixinStandardHelpOptions = true,
        subcommands = {
                ExportDbCommand.class, InitCommand.class, PrintCommand.class, RunCommand.class},
        versionProvider = FuzzTlaCommand.VersionProvider.class)
public final class FuzzTlaCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;

    private final TerminalAcquisition terminals;

    /** Creates a command that reports plainly to its configured writers, as embedded callers need. */
    public FuzzTlaCommand() {
        this(TerminalAcquisition.NONE);
    }

    FuzzTlaCommand(TerminalAcquisition terminals) {
        this.terminals = terminals;
    }

    /** The process entry point: the only caller that may take over the physical terminal. */
    public static int execute(String... args) {
        return new CommandLine(new FuzzTlaCommand(RunTerminal::open)).execute(args);
    }

    TerminalAcquisition terminals() {
        return terminals;
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return CommandLine.ExitCode.OK;
    }

    public static final class VersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            var version = FuzzTlaCommand.class.getPackage().getImplementationVersion();
            return new String[] {"fuzztla " + (version == null ? "development" : version)};
        }
    }
}
