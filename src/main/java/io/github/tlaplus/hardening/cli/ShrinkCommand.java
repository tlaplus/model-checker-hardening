package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.ConfigException;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.workflow.MetamorphicShrinker;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "shrink",
        description = "Reduce the rewrite of a metamorphic counterexample (ADR 0016).")
final class ShrinkCommand implements Callable<Integer> {
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(
            names = "--corpus",
            defaultValue = "corpus",
            paramLabel = "DIR",
            description = "Metamorphic corpus the entry belongs to (default: ${DEFAULT-VALUE}).")
    private Path corpus;

    @Option(
            names = {"-o", "--output"},
            paramLabel = "FILE",
            description = "Shrunk input. Default: <FILE name>-shrunk.cbor in the current directory.")
    private Path output;

    @Option(
            names = "--max-cpus",
            converter = RunCommand.CpuCountConverter.class,
            paramLabel = "N",
            description = "CPUs the checkers may use (default: all available processors).")
    private int maximumCpus = Runtime.getRuntime().availableProcessors();

    @Parameters(index = "0", paramLabel = "FILE", description = "CBOR corpus entry that violates the relation.")
    private Path input;

    @Spec private CommandSpec spec;

    @Override
    public Integer call() {
        try (var shutdown = RunShutdownHook.install()) {
            var directory = CorpusDirectory.openExisting(corpus);
            var config = TomlConfig.read(directory.resolve(CorpusPath.CONFIG));
            var entry = CorpusInputCodec.decode(Files.readAllBytes(input));
            var result = MetamorphicShrinker.shrink(directory, config, entry, maximumCpus);
            if (result.unrewritten()) {
                spec.commandLine().getOut().printf("fuzztla: the base violates the relation without any rewrite, so "
                        + "no rule is at fault: a checker evaluates two copies of the base differently; wrote nothing%n");
                return CommandLine.ExitCode.OK;
            }
            var destination = output != null ? output : Path.of(stem(input) + "-shrunk.cbor");
            Files.write(destination, CorpusInputCodec.encode(new CorpusInput(entry.kind(), result.input())));
            spec.commandLine().getOut().printf("fuzztla: cleared %d rewrite bits in %d checks; wrote '%s'%n",
                    result.cleared(), result.checks(), destination);
            return CommandLine.ExitCode.OK;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            CommandDiagnostic.print(spec.commandLine().getErr(), "interrupted shrinking", input, exception);
            return CommandLine.ExitCode.SOFTWARE;
        } catch (IOException | ConfigException | CorpusException | WorkflowException
                | RuntimeException exception) {
            CommandDiagnostic.print(spec.commandLine().getErr(), "cannot shrink", input, exception);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private static String stem(Path path) {
        var name = path.getFileName().toString();
        return name.endsWith(".cbor") ? name.substring(0, name.length() - ".cbor".length()) : name;
    }
}
