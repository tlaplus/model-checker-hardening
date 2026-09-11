package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine;

@Command(name = "init", description = "Initialize a FuzzTLA corpus.")
final class InitCommand implements Callable<Integer> {
    /** The system property through which {@code bin/fuzztla} names the project directory. */
    private static final String HOME_PROPERTY = "fuzztla.home";

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
        var config = FuzzTlaConfig.defaults();
        var project = projectWithShippedDatabase();
        if (project.isPresent()) {
            config = config.withKnownDefects(List.of(shippedDatabasePath(project.orElseThrow())));
        } else {
            spec.commandLine().getErr().printf(
                    "fuzztla: shipped known-defect database not found; known_defects is empty%n");
        }
        try {
            var initialized = CorpusDirectory.initialize(corpus, TomlConfig.render(config));
            spec.commandLine()
                    .getOut()
                    .printf(
                            "fuzztla: initialized corpus at '%s'%n",
                            initialized.resolve(CorpusPath.ROOT));
            return CommandLine.ExitCode.OK;
        } catch (IOException | CorpusException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot initialize corpus", corpus, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    /**
     * Returns the project directory if it holds the shipped known-defect database. {@code
     * bin/fuzztla} runs a snapshot of the JAR from a temporary directory, so it names the project
     * in {@link #HOME_PROPERTY}. Without the launcher, the JAR runs in place from the project's
     * {@code target} directory, beside the staged Apalache JAR.
     */
    private static Optional<Path> projectWithShippedDatabase() {
        var home = System.getProperty(HOME_PROPERTY);
        Optional<Path> project;
        if (home != null) {
            project = Optional.of(Path.of(home).toAbsolutePath().normalize());
        } else {
            try {
                project = Optional.ofNullable(ApalacheDistribution.locate().getParent())
                        .map(Path::getParent);
            } catch (WorkflowException exception) {
                project = Optional.empty();
            }
        }
        return project.filter(directory -> Files.isRegularFile(directory.resolve(KnownDefectDatabase.SHIPPED)));
    }

    /**
     * Names the shipped database relative to a corpus inside the project, so that the corpus keeps
     * working when the project moves, and absolutely for a corpus elsewhere, which does not move
     * with it.
     */
    private Path shippedDatabasePath(Path project) {
        var directory = corpus.toAbsolutePath().normalize();
        var database = project.resolve(KnownDefectDatabase.SHIPPED);
        return directory.startsWith(project) ? directory.relativize(database) : database;
    }
}
