package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.pbt.PbtException;
import io.github.tlaplus.hardening.pbt.PbtRunSummary;
import io.github.tlaplus.hardening.pbt.PbtRunner;
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

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        return switch (technique) {
            case PBT -> runPbt();
        };
    }

    /** Runs the initial property-based corpus generation design. */
    private int runPbt() {
        try {
            var directory = CorpusDirectory.open(corpus);
            var config = directory.readConfig();
            var effectiveSeed = seed == null ? randomSeed() : seed;
            var summary = new PbtRunner(config).run(directory, effectiveSeed);
            printSummary(directory, summary);
            return CommandLine.ExitCode.OK;
        } catch (PbtException exception) {
            var summary = exception.summary();
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: PBT failed in '%s': %s%n%n",
                            corpus, CliDiagnostics.message(exception));
            printCounters(spec.commandLine().getErr(), summary);
            return CommandLine.ExitCode.SOFTWARE;
        } catch (IOException | ConfigException | CorpusException exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: cannot run PBT in '%s': %s%n",
                            corpus, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        } catch (RuntimeException | StackOverflowError exception) {
            spec.commandLine()
                    .getErr()
                    .printf(
                            "fuzztla: PBT generation failed in '%s': %s%n",
                            corpus, CliDiagnostics.message(exception));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    /** Prints replay information and all corpus-generation counters. */
    private void printSummary(CorpusDirectory directory, PbtRunSummary summary) {
        var out = spec.commandLine().getOut();
        out.printf("PBT run finished for '%s'%n%n", directory.inputDirectory());
        printCounters(out, summary);
    }

    /** Prints counters as an aligned JUnit-style summary block. */
    private static void printCounters(PrintWriter writer, PbtRunSummary summary) {
        printCounter(writer, summary.seed(), "random seed");
        printCounter(writer, summary.existing() + summary.added(), "corpus entries");
        printCounter(writer, summary.existing(), "existing entries");
        printCounter(writer, summary.added(), "added entries");
        printCounter(writer, summary.attempts(), "attempted inputs");
        printCounter(writer, summary.rejected(), "rejected inputs");
        printCounter(writer, summary.duplicates(), "duplicate inputs");
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
}
