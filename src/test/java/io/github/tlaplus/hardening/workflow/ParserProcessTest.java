package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserProcessTest {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void reusesAWorkerAndOneResolverForFullSanyResults(@TempDir Path directory)
            throws Exception {
        var valid = validSource();
        var semanticFailure = valid.replace("FALSE", "MissingName");
        var syntaxFailure = valid.replace("FALSE", "ENABLED TRUE'");
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        try (var worker = ParserProcess.start(scratch, STARTUP_TIMEOUT)) {
            assertEquals(
                    ParserResult.Outcome.PASS,
                    worker.parse(valid, STARTUP_TIMEOUT).outcome());
            assertEquals(
                    ParserResult.Outcome.FAIL,
                    worker.parse(semanticFailure, STARTUP_TIMEOUT).outcome());
            assertEquals(
                    ParserResult.Outcome.FAIL,
                    worker.parse(syntaxFailure, STARTUP_TIMEOUT).outcome());
            assertEquals(
                    ParserResult.Outcome.PASS,
                    worker.parse(valid, STARTUP_TIMEOUT).outcome());
            try (var paths = Files.walk(scratch)) {
                assertEquals(
                        1,
                        paths.filter(Files::isDirectory)
                                .filter(path -> path.getFileName()
                                        .toString()
                                        .startsWith("tlc-"))
                                .count());
            }
        }
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void resolvesPackagedApalacheAndVariantModules(@TempDir Path directory)
            throws Exception {
        var source = """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLE exprValue
                Init == exprValue = <<Expand({1}), Variant("tag", 1)>>
                Next == UNCHANGED exprValue
                Inv == exprValue = <<Expand({1}), Variant("tag", 1)>>
                ====
                """;

        var scratch = Files.createDirectory(directory.resolve("scratch"));
        try (var worker = ParserProcess.start(scratch, STARTUP_TIMEOUT)) {
            assertEquals(
                    ParserResult.Outcome.PASS,
                    worker.parse(source, STARTUP_TIMEOUT).outcome());
        }
    }

    @Test
    void killsATimedOutWorkerAndAllowsAReplacement(@TempDir Path directory)
            throws Exception {
        var valid = validSource();
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        try (var worker = ParserProcess.start(scratch, STARTUP_TIMEOUT)) {
            assertEquals(
                    ParserResult.Outcome.CRASH,
                    worker.parse(valid, Duration.ZERO).outcome());
        }
        try (var replacement = ParserProcess.start(scratch, STARTUP_TIMEOUT)) {
            assertEquals(
                    ParserResult.Outcome.PASS,
                    replacement.parse(valid, STARTUP_TIMEOUT).outcome());
        }
    }

    @Test
    void includesWorkerStderrInStartupFailures(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        var failure = assertThrows(
                WorkflowException.class,
                () -> ParserProcess.start(
                        scratch, STARTUP_TIMEOUT, StartupFailureWorker.class));

        assertTrue(failure.getMessage().contains("parser worker failed during startup"));
        assertTrue(failure.getMessage().contains("deliberate startup failure"));
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void includesWorkerStderrWhenItExitsDuringParsing(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        ParserResult result;
        try (var worker = ParserProcess.start(
                scratch, STARTUP_TIMEOUT, ProcessingFailureWorker.class)) {
            result = worker.parse(validSource(), STARTUP_TIMEOUT);
        }

        assertEquals(ParserResult.Outcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().contains("exited while processing"));
        assertTrue(result.diagnostic().contains("deliberate processing failure"));
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    private String validSource() {
        var expression = IrGenerators.expressions().generate(new byte[0]);
        var module = FuzzInputModule.create(expression);
        return PrettyWriter.writeAsString(
                module, TlaWriter$.MODULE$.STANDARD_MODULES());
    }

    public static final class StartupFailureWorker {
        private StartupFailureWorker() {}

        public static void main(String[] ignoredArguments) {
            System.err.println("deliberate startup failure");
        }
    }

    public static final class ProcessingFailureWorker {
        private ProcessingFailureWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            var output = new java.io.DataOutputStream(
                    new java.io.BufferedOutputStream(System.out));
            output.writeInt(ParserWorkerProtocol.MAGIC);
            output.writeInt(ParserWorkerProtocol.VERSION);
            output.flush();

            var input = new java.io.DataInputStream(System.in);
            var length = input.readInt();
            input.readNBytes(length);
            System.err.println("deliberate processing failure");
        }
    }
}
