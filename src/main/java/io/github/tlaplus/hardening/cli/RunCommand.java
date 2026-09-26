package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.WorkflowRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;
import picocli.CommandLine;

@Command(name = "run", description = "Run specification fuzzing.")
final class RunCommand implements Callable<Integer> {
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
            description = "Fuzzing technique to use (currently: pbt).")
    private Technique technique;

    @Option(
            names = "--corpus",
            defaultValue = "corpus",
            paramLabel = "DIR",
            description = "Corpus directory (default: ${DEFAULT-VALUE}).")
    private Path corpus;

    @Option(
            names = "--seed",
            converter = SeedConverter.class,
            paramLabel = "SEED",
            description =
                    "Nonnegative 64-bit seed used as the base for deterministic generator-worker streams.")
    private Long seed;

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
        return switch (technique) {
            case PBT -> runPbt();
        };
    }

    /** Runs property-based generation, parsing, TLC, and Apalache as one concurrent workflow. */
    private int runPbt() {
        // Shutdown waits until terminal restoration and diagnostics have completed as well.
        try (var shutdown = RunShutdownHook.install()) {
            return executePbt();
        }
    }

    private int executePbt() {
        try {
            var effectiveSeed = seed == null ? randomSeed() : seed;
            spec.commandLine().getOut().printf("Random seed: %d%n", effectiveSeed);
            spec.commandLine().getOut().flush();
            var directory = CorpusDirectory.openExisting(corpus);
            var config = TomlConfig.read(directory.resolve(CorpusPath.CONFIG));
            // Closing the output restores the terminal before a diagnostic is printed.
            try (var output = RunOutput.open(spec.commandLine().getOut(), parent.terminals(),
                    config.mutator().feedbackRatio() > 0)) {
                var summary = output.run(new WorkflowRunner(config, technique), directory, effectiveSeed, maximumCpus);
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

    /** Draws an unpredictable seed while clearing the sign bit. */
    private static long randomSeed() {
        return new SecureRandom().nextLong() & Long.MAX_VALUE;
    }

    public static final class TechniqueConverter implements ITypeConverter<Technique> {
        @Override
        public Technique convert(String value) {
            return Technique.fromEncodedName(value).orElseThrow(() -> new TypeConversionException(
                    "expected one of: " + String.join(", ", Technique.encodedNames())));
        }
    }

    public static final class SeedConverter implements ITypeConverter<Long> {
        @Override
        public Long convert(String value) {
            try {
                var result = Long.parseLong(value);
                if (result < 0) {
                    throw new NumberFormatException();
                }
                return result;
            } catch (NumberFormatException exception) {
                throw new TypeConversionException(
                        "expected an integer in the range 0.." + Long.MAX_VALUE);
            }
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
