package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ParserProcessTest {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void reusesAWorkerForFullSanyPassAndFailureResults() throws Exception {
        var valid = validSource();
        var semanticFailure = valid.replace("FALSE", "MissingName");
        var syntaxFailure = valid.replace("FALSE", "ENABLED TRUE'");

        try (var worker = ParserProcess.start(STARTUP_TIMEOUT)) {
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
        }
    }

    @Test
    void resolvesPackagedApalacheAndVariantModules() throws Exception {
        var source = """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLE exprValue
                Init == exprValue = <<Expand({1}), Variant("tag", 1)>>
                Next == UNCHANGED exprValue
                Inv == exprValue = <<Expand({1}), Variant("tag", 1)>>
                ====
                """;

        try (var worker = ParserProcess.start(STARTUP_TIMEOUT)) {
            assertEquals(
                    ParserResult.Outcome.PASS,
                    worker.parse(source, STARTUP_TIMEOUT).outcome());
        }
    }

    @Test
    void killsATimedOutWorkerAndAllowsAReplacement() throws Exception {
        var valid = validSource();

        try (var worker = ParserProcess.start(STARTUP_TIMEOUT)) {
            assertEquals(
                    ParserResult.Outcome.CRASH,
                    worker.parse(valid, Duration.ZERO).outcome());
        }
        try (var replacement = ParserProcess.start(STARTUP_TIMEOUT)) {
            assertEquals(
                    ParserResult.Outcome.PASS,
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
