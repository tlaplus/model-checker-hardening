package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.WorkflowRunner;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;

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
            description = "Nonnegative 64-bit seed for reproducible input generation.")
    private Long seed;

    @Option(
            names = "--max-cpus",
            converter = CpuCountConverter.class,
            paramLabel = "N",
            description = "Maximum active stage jobs (default: all available processors).")
    private int maximumCpus = Runtime.getRuntime().availableProcessors();

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        return switch (technique) {
            case PBT -> runPbt();
        };
    }

    /** Runs property-based generation and parsing as one concurrent workflow. */
    private int runPbt() {
        try {
            var directory = CorpusDirectory.open(corpus);
            var config = directory.readConfig();
            var effectiveSeed = seed == null ? randomSeed() : seed;
            var summary = new WorkflowRunner(config)
                    .run(directory, effectiveSeed, maximumCpus);
            printSummary(directory, summary);
            return CommandLine.ExitCode.OK;
        } catch (IOException | ConfigException | CorpusException | WorkflowException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot run workflow in '%s': %s%n",
                            corpus, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        } catch (RuntimeException | StackOverflowError exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: workflow failed in '%s': %s%n",
                            corpus, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    /** Prints replay information and stage counters. */
    private void printSummary(CorpusDirectory directory, WorkflowRunSummary summary) {
        var out = spec.commandLine().getOut();
        out.printf("Workflow run finished for '%s'%n%n", directory.root());
        printCounter(out, summary.generator().seed(), "random seed");
        printCounter(out, summary.corpus().totalEntries(), "corpus entries");
        printCounter(out, summary.corpus().inputEntries(), "remaining inputs");
        printCounter(out, summary.generator().added(), "generated inputs");
        printCounter(out, summary.parser().passed(), "parser passed");
        printCounter(out, summary.parser().failed(), "parser failed");
        printCounter(out, summary.parser().crashed(), "parser crashed");
        out.printf("[%20s %-18s]%n", summary.stopReason(), "stop reason");
    }

    /** Prints one aligned row in the summary block. */
    private static void printCounter(PrintWriter writer, long value, String label) {
        writer.printf("[%20d %-18s]%n", value, label);
    }

    /** Draws an unpredictable seed while clearing the sign bit. */
    private static long randomSeed() {
        return new SecureRandom().nextLong() & Long.MAX_VALUE;
    }

    enum Technique {
        PBT("pbt");

        private final String cliName;

        Technique(String cliName) {
            this.cliName = cliName;
        }

        String cliName() {
            return cliName;
        }

        static Technique fromCliName(String value) {
            return Arrays.stream(values())
                    .filter(technique -> technique.cliName.equals(value))
                    .findFirst()
                    .orElseThrow(() -> new TypeConversionException(
                            "expected one of: "
                                    + String.join(", ", Arrays.stream(values())
                                            .map(Technique::cliName)
                                            .toList())));
        }
    }

    public static final class TechniqueConverter implements ITypeConverter<Technique> {
        @Override
        public Technique convert(String value) {
            return Technique.fromCliName(value);
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
