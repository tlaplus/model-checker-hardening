package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.workflow.AdoptableBase;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.LibraryInstall;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine;

/**
 * {@code fuzztla init}: writes the configuration of a new corpus, which names the technique every
 * run follows (ADR 0025 §1), and copies its libraries and tool JARs into it (§2, §3).
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

    @Option(
            names = "--base-corpus",
            paramLabel = "DIR",
            description = "With --how=mt, a pbt or gbt corpus whose selected entries the corpus adopts;"
                    + " the corpus starts from its configuration, libraries and tools.")
    private Path baseCorpus;

    @Option(
            names = "--temporal",
            description = "With --how=mt, also rewrite properties and fairness with the shipped temporal rules"
                    + " and check them with the corpus's checkers (ADR 0027, ADR 0029); the corpus generates"
                    + " modules. With Apalache, fairness and ENABLED are quarantined.")
    private boolean temporal;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        if (baseCorpus != null && technique != Technique.MT) {
            return usage("--base-corpus requires --how=mt");
        }
        if (baseCorpus != null && library != null) {
            return usage("--base-corpus names the library already; leave out --library");
        }
        if (temporal && technique != Technique.MT) {
            return usage("--temporal requires --how=mt");
        }
        var initial = new InitialConfig(corpus, InitialConfig.locateProject(), InitialConfig.randomSeed());
        FuzzTlaConfig config;
        if (baseCorpus == null) {
            config = initial.create(technique);
        } else {
            try {
                config = initial.inherit(AdoptableBase.configuration(baseCorpus), baseCorpus);
            } catch (IOException | CorpusException | WorkflowException exception) {
                CommandDiagnostic.print(spec.commandLine().getErr(), "cannot adopt from", baseCorpus, exception);
                return CommandLine.ExitCode.USAGE;
            }
        }
        if (!initial.hasProject()) {
            spec.commandLine().getErr().printf(
                    "fuzztla: shipped known-defect database not found; known_defects is empty%n");
            if (technique == Technique.MT) {
                spec.commandLine().getErr().printf(
                        "fuzztla: shipped rewrite rules not found; set [metamorphic] rules%n");
            }
            if (temporal) {
                return usage("--temporal needs the shipped temporal rules, which were not found");
            }
        }
        if (temporal) {
            if (!SpecDecoders.hasProperties(config)) {
                return usage("--temporal needs kind = \"module\", or kind = \"graph\" with [graph] properties = true:"
                        + " only these have a property, and the base generates " + config.generatedKind().encodedName()
                        + (config.generatedKind() == InputKind.GRAPH ? " without properties" : ""));
            }
            if (config.generator().ignoredCategories().contains(ExpressionCategory.TEMPORAL)) {
                spec.commandLine().getErr().printf(
                        "fuzztla: the generator ignores temporal, so no module has a property to rewrite%n");
            }
            config = initial.withTemporalRules(config);
        }
        if (library != null) {
            try {
                config = config.withLibraries(TomlConfig.readLibrary(library));
            } catch (IOException | ConfigException exception) {
                CommandDiagnostic.print(spec.commandLine().getErr(), "cannot read library", library, exception);
                return CommandLine.ExitCode.USAGE;
            }
        }
        final LibraryInstall.Plan plan;
        try {
            plan = LibraryInstall.plan(config);
        } catch (WorkflowException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot install the libraries and tools of", corpus,
                    exception);
            return CommandLine.ExitCode.USAGE;
        }
        try {
            var initialized = CorpusDirectory.initialize(corpus, TomlConfig.render(plan.config()));
            LibraryInstall.copy(plan, initialized.resolve(CorpusPath.ROOT));
            spec.commandLine()
                    .getOut()
                    .printf(
                            "fuzztla: initialized %s corpus at '%s' with random seed %d%s%n",
                            technique.encodedName(),
                            initialized.resolve(CorpusPath.ROOT),
                            plan.config().seeds().generator(),
                            baseCorpus == null ? "" : ", adopting from '" + baseCorpus + "'");
            return CommandLine.ExitCode.OK;
        } catch (IOException | CorpusException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot initialize corpus", corpus, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private int usage(String message) {
        spec.commandLine().getErr().printf("fuzztla: %s%n", message);
        return CommandLine.ExitCode.USAGE;
    }
}
