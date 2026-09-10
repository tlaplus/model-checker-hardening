package io.github.tlaplus.hardening.workflow.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
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
                    worker.request(new ToolInput(valid, 0), STARTUP_TIMEOUT).outcome());
            assertEquals(
                    StageOutcome.FAIL,
                    worker.request(new ToolInput(semanticFailure, 0), STARTUP_TIMEOUT).outcome());
            assertEquals(
                    StageOutcome.FAIL,
                    worker.request(new ToolInput(syntaxFailure, 0), STARTUP_TIMEOUT).outcome());
            assertEquals(
                    StageOutcome.PASS,
                    worker.request(new ToolInput(valid, 0), STARTUP_TIMEOUT).outcome());
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
                    worker.request(new ToolInput(source, 0), STARTUP_TIMEOUT).outcome());
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
                    worker.request(new ToolInput(valid, 0), Duration.ZERO).outcome());
        }
        try (var replacement = ParserProcess.start(scratch, STARTUP_TIMEOUT)) {
            assertEquals(
                    StageOutcome.PASS,
                    replacement.request(new ToolInput(valid, 0), STARTUP_TIMEOUT).outcome());
        }
    }

    @Test
    void everyGeneratedModuleParses(@TempDir Path directory) throws Exception {
        // The generator promises a module that names only what it declares and that SANY
        // accepts. SANY is the authority on both, so this is checked here rather than
        // restated in a unit test.
        //
        // This assertion is deliberately unconditional. It once tolerated two label failures
        // -- a label under a binder without mentioning it, and a label inside an EXCEPT -- and
        // that tolerance hid a defect that rejected 58% of a module-kind corpus at the parser.
        // Nothing about a generated module may fail to parse.
        // Only the shipped configuration is asserted here. Enabling the unbound category as
        // well reaches shapes where PrettyWriter prints an undelimited CHOOSE or CASE as a CASE
        // arm body, so the source parses to a different tree than the IR and a later arm ends up
        // inside the CHOOSE's scope. That is a printer defect, not a generator one, and the
        // generator's own contract is asserted on the IR by IrSpecGeneratorsTest across every
        // category filter.
        var generator = IrGenerators.specs(IrGenerationConfig.defaults());
        var random = new Random(0x5a4eL);
        var scratch = Files.createDirectory(directory.resolve("scratch"));
        var parsed = 0;
        var generated = 0;

        try (var worker = ParserProcess.start(scratch, STARTUP_TIMEOUT)) {
            for (var sample = 0; sample < 40; sample++) {
                var input = new byte[64 + random.nextInt(448)];
                random.nextBytes(input);
                final String source;
                try {
                    source = SpecText.render(
                            FuzzInputModule.create(generator.generate(input)));
                } catch (InputRejectedException rejected) {
                    continue;
                }
                generated++;
                var result = worker.request(new ToolInput(source, 0), STARTUP_TIMEOUT);
                assertEquals(
                        StageOutcome.PASS,
                        result.outcome(),
                        result.diagnostic() + "\n" + source);
                parsed++;
            }
        }

        assertTrue(generated > 20, "too few modules were generated to be conclusive: " + generated);
        assertEquals(generated, parsed);
    }

    private String validSource() {
        var expression = IrGenerators.expressions().generate(new byte[0]);
        var module = FuzzInputModule.create(expression);
        return PrettyWriter.writeAsString(
                module, TlaWriter$.MODULE$.STANDARD_MODULES());
    }

}
