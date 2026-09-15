package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.database.CorpusDatabaseException;
import io.github.tlaplus.hardening.database.CorpusExport;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "export-db",
        description = "Export the entries of a corpus to a new SQLite database for analysis.")
final class ExportDbCommand implements Callable<Integer> {
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(
            names = {"-o", "--output"},
            paramLabel = "FILE",
            description = "Database file. Default: corpus.sqlite in the corpus directory.")
    private Path output;

    @Option(names = "--force", description = "Replace an existing database file.")
    private boolean force;

    @Option(
            names = "--no-lock",
            description = "Do not take the corpus lock, so that a running workflow can be exported."
                    + " The export is then not a consistent snapshot.")
    private boolean noLock;

    @Option(
            names = "--corpus",
            defaultValue = "corpus",
            paramLabel = "DIR",
            description = "Corpus directory (default: ${DEFAULT-VALUE}).")
    private Path corpus;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        var options = new CorpusExport.Options(
                corpus,
                output == null ? CorpusExport.defaultOutput(corpus) : output,
                force,
                !noLock,
                new FuzzTlaCommand.VersionProvider().getVersion()[0],
                Instant.now());
        try {
            var summary = CorpusExport.run(options);
            spec.commandLine().getOut().print(ExportDbReport.render(summary));
            spec.commandLine().getOut().flush();
            return CommandLine.ExitCode.OK;
        } catch (IOException | CorpusException | CorpusDatabaseException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot export", corpus, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
