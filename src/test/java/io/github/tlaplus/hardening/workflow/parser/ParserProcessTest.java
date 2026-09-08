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
    void generatedModulesResolveEveryNameTheyUse(@TempDir Path directory) throws Exception {
        // The generator promises that a module names only what it declares. SANY is the
        // authority on that, so it is checked here rather than restated in a unit test.
        //
        // Not every generated module parses, and that is not a module-level defect: the
        // expression decoder can place a label under a binder without mentioning it, or inside
        // an EXCEPT, both of which SANY rejects. Those failures occur at the same rate for
        // expression inputs, so what is asserted here is that nothing else fails.
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
                if (result.outcome() == StageOutcome.PASS) {
                    parsed++;
                    continue;
                }
                assertTrue(
                        isKnownLabelLimitation(result.diagnostic()),
                        result.diagnostic() + "\n" + source);
            }
        }

        assertTrue(generated > 20, "too few modules were generated to be conclusive: " + generated);
        assertTrue(parsed > generated / 2, "only " + parsed + " of " + generated + " parsed");
    }

    /** Reports whether a parser failure is one of the two label limitations of the decoder. */
    private boolean isKnownLabelLimitation(String diagnostic) {
        return diagnostic.contains("must contain formal parameter")
                || diagnostic.contains("Labels inside EXCEPT clauses are not yet implemented");
    }

    private String validSource() {
        var expression = IrGenerators.expressions().generate(new byte[0]);
        var module = FuzzInputModule.create(expression);
        return PrettyWriter.writeAsString(
                module, TlaWriter$.MODULE$.STANDARD_MODULES());
    }

}
