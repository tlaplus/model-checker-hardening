package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.database.CorpusDatabaseException;
import io.github.tlaplus.hardening.database.CorpusExport;
import io.github.tlaplus.hardening.database.InputAnalysis;
import io.github.tlaplus.hardening.database.InputFeatures;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.LibraryManifest;
import io.github.tlaplus.hardening.workflow.spec.EvaluatedOperators;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
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
            names = "--corpus",
            defaultValue = "corpus",
            paramLabel = "DIR",
            description = "Corpus directory (default: ${DEFAULT-VALUE}).")
    private Path corpus;

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
            names = "--max-cpus",
            converter = RunCommand.CpuCountConverter.class,
            paramLabel = "N",
            description = "Threads that replay inputs (default: all available processors).")
    private int maximumCpus = Runtime.getRuntime().availableProcessors();

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        var options = new CorpusExport.Options(
                corpus,
                output == null ? CorpusExport.defaultOutput(corpus) : output,
                force,
                !noLock,
                new CorpusExport.Provenance(
                        new FuzzTlaCommand.VersionProvider().getVersion()[0], Instant.now()));
        try (var shutdown = RunShutdownHook.install()) {
            var analysis = new CorpusExport.Analysis(replay(), maximumCpus);
            var summary = CorpusExport.run(options, analysis);
            spec.commandLine().getOut().print(ExportDbReport.render(summary));
            spec.commandLine().getOut().flush();
            return CommandLine.ExitCode.OK;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            spec.commandLine().getErr().printf("fuzztla: export of '%s' interrupted%n", corpus);
            return CommandLine.ExitCode.SOFTWARE;
        } catch (IOException
                | ConfigException
                | CorpusException
                | CorpusDatabaseException
                | WorkflowException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot export", corpus, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    /** Replays inputs under the corpus's own generator settings and operator library. */
    private InputAnalysis replay()
            throws IOException, ConfigException, CorpusException, WorkflowException {
        var directory = CorpusDirectory.openExisting(corpus);
        var decoders = SpecDecoders.prepare(TomlConfig.read(directory.resolve(CorpusPath.CONFIG)));
        LibraryManifest.verify(directory, decoders.libraryManifest(), false);
        var operators = new EvaluatedOperators(decoders);
        return input -> {
            var counts = operators.count(input);
            return new InputFeatures(counts.nodes(), counts.operators());
        };
    }
}
