package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.workflow.RunMode;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.WorkflowRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;
import picocli.CommandLine;

/**
 * {@code fuzztla run}: reads a corpus configuration and runs {@code WorkflowRunner} with live
 * progress output. The configuration names the technique, which {@code fuzztla init --how} chose
 * (ADR 0025 §1), and the seeds (ADR 0026). Only {@code --continue} extends a corpus that is not
 * fresh.
 */
@ArchRole(Role.ENTRY_POINT)
@Key
@Command(name = "run", description = "Run specification fuzzing.")
final class RunCommand implements Callable<Integer> {
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
            names = "--continue",
            description = "Extend a corpus that an earlier run has written, and mark it as not"
                    + " reproducible from its configuration.")
    private boolean continueRun;

    @Option(
            names = "--max-cpus",
            converter = CpuCountConverter.class,
            paramLabel = "N",
            description = "Maximum active stage jobs (default: all available processors).")
    private int maximumCpus = Runtime.getRuntime().availableProcessors();

    @ParentCommand private FuzzTlaCommand parent;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        // Every technique runs the same workflow; the configuration names which one it runs.
        // Shutdown waits until terminal restoration and diagnostics have completed as well.
        try (var shutdown = RunShutdownHook.install()) {
            return execute();
        }
    }

    /** Runs generation, parsing, the checkers, aggregation and the quality gate as one workflow. */
    private int execute() {
        var out = spec.commandLine().getOut();
        try {
            var directory = CorpusDirectory.openExisting(corpus);
            var config = TomlConfig.read(directory.resolve(CorpusPath.CONFIG));
            out.printf("Random seed: %d%n", config.seeds().generator());
            out.printf("Technique: %s%n", config.workflow().technique().encodedName());
            out.flush();
            var mode = continueRun ? RunMode.CONTINUE : RunMode.FRESH;
            if (!directory.isFresh()) {
                if (mode == RunMode.FRESH) {
                    spec.commandLine().getErr().printf(
                            "fuzztla: corpus '%s' has been run before; pass --continue to extend it,"
                                    + " which marks it as not reproducible%n", corpus);
                    return CommandLine.ExitCode.USAGE;
                }
                spec.commandLine().getErr().printf(
                        "fuzztla: warning: continuing corpus '%s', which is now marked as not reproducible (%s)%n",
                        corpus, CorpusPath.NOT_REPRODUCIBLE.relativePath());
                spec.commandLine().getErr().flush();
            }
            // Closing the output restores the terminal before a diagnostic is printed.
            try (var output = RunOutput.open(out, parent.terminals(),
                    config.mutator().feedbackRatio() > 0)) {
                var summary = output.run(new WorkflowRunner(config), directory, mode, maximumCpus);
                output.finish(directory.resolve(CorpusPath.ROOT), summary);
            }
            return CommandLine.ExitCode.OK;
        } catch (IOException | ConfigException | CorpusException | WorkflowException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot run workflow in", corpus, exception);
            return CommandLine.ExitCode.SOFTWARE;
        } catch (RuntimeException | StackOverflowError exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "workflow failed in", corpus, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    public static final class CpuCountConverter implements ITypeConverter<Integer> {
        @Override
        public Integer convert(String value) {
            var maximum = Runtime.getRuntime().availableProcessors();
            try {
                var result = Integer.parseInt(value);
                if (result <= 0 || result > maximum) {
                    throw new NumberFormatException();
                }
                return result;
            } catch (NumberFormatException exception) {
                throw new TypeConversionException(
                        "expected an integer in the range 1.." + maximum);
            }
        }
    }
}
