package io.github.tlaplus.hardening;

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
        subcommands = {InitCommand.class, RunCommand.class},
        versionProvider = FuzzTlaCommand.VersionProvider.class)
final class FuzzTlaCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;

    static int execute(String... args) {
        return new CommandLine(new FuzzTlaCommand()).execute(args);
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
