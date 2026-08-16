package io.github.tlaplus.hardening.workflow.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
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
                    StageOutcome.PASS,
                    worker.parse(valid, STARTUP_TIMEOUT).outcome());
            assertEquals(
                    StageOutcome.FAIL,
                    worker.parse(semanticFailure, STARTUP_TIMEOUT).outcome());
            assertEquals(
                    StageOutcome.FAIL,
                    worker.parse(syntaxFailure, STARTUP_TIMEOUT).outcome());
            assertEquals(
                    StageOutcome.PASS,
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
                    StageOutcome.PASS,
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
                    StageOutcome.CRASH,
                    worker.parse(valid, Duration.ZERO).outcome());
        }
        try (var replacement = ParserProcess.start(scratch, STARTUP_TIMEOUT)) {
            assertEquals(
                    StageOutcome.PASS,
                    replacement.parse(valid, STARTUP_TIMEOUT).outcome());
        }
    }

    private String validSource() {
        var expression = IrGenerators.expressions().generate(new byte[0]);
        var module = FuzzInputModule.create(expression);
        return PrettyWriter.writeAsString(
                module, TlaWriter$.MODULE$.STANDARD_MODULES());
    }

}
