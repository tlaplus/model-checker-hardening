package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "init", description = "Initialize a FuzzTLA corpus.")
final class InitCommand implements Callable<Integer> {
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
        try {
            var initialized = CorpusDirectory.initialize(corpus);
            spec.commandLine()
                    .getOut()
                    .printf(
                            "fuzztla: initialized corpus at '%s'%n",
                            initialized.resolve(CorpusPath.ROOT));
            return CommandLine.ExitCode.OK;
        } catch (IOException | CorpusException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot initialize corpus '%s': %s%n",
                            corpus, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
