package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.LibraryInstall;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine;

/**
 * {@code fuzztla init}: writes the configuration of a new corpus, which names the technique every
 * run follows (ADR 0025 §1), and installs its libraries.
 */
@ArchRole(Role.ENTRY_POINT)
@Key
@Command(name = "init", description = "Initialize a FuzzTLA corpus.")
final class InitCommand implements Callable<Integer> {
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(
            names = "--how",
            required = true,
            converter = TechniqueConverter.class,
            paramLabel = "TECHNIQUE",
            description = TechniqueConverter.DESCRIPTION)
    private Technique technique;

    @Option(
            names = "--corpus",
            defaultValue = "corpus",
            paramLabel = "DIR",
            description = "Corpus directory (default: ${DEFAULT-VALUE}).")
    private Path corpus;

    @Option(
            names = "--library",
            paramLabel = "FILE",
            description = "Library file whose [generator] classpath and custom_operators the corpus"
                    + " uses; its classpath is copied into the corpus.")
    private Path library;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        var initial = new InitialConfig(corpus, InitialConfig.locateProject());
        var config = initial.create(technique);
        if (!initial.hasProject()) {
            spec.commandLine().getErr().printf(
                    "fuzztla: shipped known-defect database not found; known_defects is empty%n");
            if (technique == Technique.MT) {
                spec.commandLine().getErr().printf(
                        "fuzztla: shipped rewrite rules not found; set [metamorphic] rules%n");
            }
        }
        Optional<OperatorLibraryConfig> source = Optional.empty();
        if (library != null) {
            try {
                source = Optional.of(TomlConfig.readLibrary(library));
                config = config.withLibraries(LibraryInstall.installed(source.orElseThrow()));
            } catch (IOException | ConfigException | WorkflowException exception) {
                CommandDiagnostic.print(spec.commandLine().getErr(), "cannot read library", library, exception);
                return CommandLine.ExitCode.USAGE;
            }
        }
        try {
            var initialized = CorpusDirectory.initialize(corpus, TomlConfig.render(config));
            if (source.isPresent()) LibraryInstall.copy(source.orElseThrow(), initialized.resolve(CorpusPath.ROOT));
            spec.commandLine()
                    .getOut()
                    .printf(
                            "fuzztla: initialized %s corpus at '%s'%n",
                            technique.encodedName(),
                            initialized.resolve(CorpusPath.ROOT));
            return CommandLine.ExitCode.OK;
        } catch (IOException | CorpusException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot initialize corpus", corpus, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
