package io.github.tlaplus.hardening.cli;

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

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine()
                .getErr()
                .println("fuzztla: run --how=" + technique.cliName() + " is not implemented yet");
        return CommandLine.ExitCode.SOFTWARE;
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
}
