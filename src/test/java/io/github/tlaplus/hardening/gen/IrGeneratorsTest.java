package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.engine.IrGeneratorEngine;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IrGeneratorsTest {
    @Test
    void emptyInputProducesFalse() {
        assertEquals("FALSE", print(IrGenerators.expressions().generate(new byte[0])));
    }

    @Test
    void generationIsDeterministic() {
        var input = new byte[] {7, 1, 3, 1, 9, 0, 5, 12, 1, 8, 0};

        var first = print(IrGenerators.expressions().generate(input));
        var second = print(IrGenerators.expressions().generate(input));

        assertEquals(first, second);
    }

    @Test
    void publicEngineMatchesTheGeneratorFactory() {
        var input = new byte[] {7, 1, 3, 1, 9, 0, 5, 12, 1, 8, 0};
        var config = IrGenerationConfig.defaults();

        var fromFactory = IrGenerators.expressions(config).generate(input);
        var fromEngine = new IrGeneratorEngine(config).generate(new Draw(input));

        assertEquals(print(fromFactory), print(fromEngine));
    }

    @Test
    void engineCanBeReusedWithoutLeakingRunState() {
        var input = new byte[] {7, 1, 3, 1, 9, 0, 5, 12, 1, 8, 0};
        var engine = new IrGeneratorEngine(IrGenerationConfig.defaults());

        var first = print(engine.generate(new Draw(input)));
        var second = print(engine.generate(new Draw(input)));

        assertEquals(first, second);
    }

    @Test
    void nestedRecordSetFormRemainsRowTyped() {
        var input = Base64.getDecoder().decode("LNehJNvsP7MYvY+AwsbJ/oNuCdm3JRQxvq0=");

        assertFalse(print(IrGenerators.expressions().generate(input)).isEmpty());
    }

    @Test
    void shortAndAdversarialInputsBuildPrintOrRejectCleanly() {
        for (var value = 0; value < 256; value++) {
            assertBuildsOrRejects(new byte[] {(byte) value});
            assertBuildsOrRejects(new byte[] {0, (byte) value});
        }

        var random = new Random(0x5eedL);
        for (var sample = 0; sample < 5000; sample++) {
            var input = new byte[random.nextInt(65)];
            random.nextBytes(input);
            assertBuildsOrRejects(input);
        }

        var full = new byte[8192];
        java.util.Arrays.fill(full, (byte) 0xff);
        var bounded = new IrGenerationConfig(
                3,
                12,
                256,
                8,
                32,
                16,
                IrGenerationConfig.defaults().ignoredCategories());
        assertBuildsOrRejects(bounded, full);

        var longStructuredInput = Base64.getMimeDecoder()
                .decode(
                        """
                        PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHByb2plY3QgeG1sbnM9
                        Imh0dHA6Ly9tYXZlbi5hcGFjaGUub3JnL1BPTS80LjAuMCIKICAgICAgICAgeG1sbnM6eHNp
                        PSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYS1pbnN0YW5jZSIKICAgICAgIC
                        AgeHNpOnNjaGVtYUxvY2F0aW9uPSJodHRwOi8vbWF2ZW4uYXBhY2hlLm9yZy9QT00vNC4w
                        LjAgaHR0cHM6Ly9tYXZlbi5hcGFjaGUub3JnL3hzZC9tYXZlbi00LjAuMC54c2QiPgogIDxt
                        b2RlbFZlcnNpb24+NC4wLjA8L21vZGVsVmVyc2lvbj4KCiAgPGdyb3VwSWQ+aW8uZ2l0aHVi
                        LnRsYXBsdXM8L2dyb3VwSWQ+CiAgPGFydGlmYWN0SWQ+aGFyZGVuaW5nPC9hcnRpZmFjdElk
                        PgogIDx2ZXJzaW9uPjAuMS4wLVNOQVBTSE9UPC92ZXJzaW9uPgoKICA8bmFtZT5GdXp6VExB
                        PC9uYW1lPgogIDxkZXNjcmlwdGlvbj5Ub29scyBmb3Igc3ludGhlc2l6aW5nIFRMQSsgc3Bl
                        Y2lmaWNhdGlvbnMgdG8gaGFyZGVuIG1vZGVsIGM=
                        """);
        assertBuildsOrRejects(longStructuredInput);
    }

    @Test
    void everyCategoryFilterBuildsAdversarialInputsCleanly() {
        var random = new Random(0xca7e60L);
        for (var category : ExpressionCategory.values()) {
            if (!category.isIgnorable()) {
                continue;
            }
            var config = configIgnoring(Set.of(category));
            for (var sample = 0; sample < 128; sample++) {
                var input = new byte[random.nextInt(65)];
                random.nextBytes(input);
                assertBuildsOrRejects(config, input);
            }
        }
    }

    private void assertBuildsOrRejects(byte[] input) {
        assertBuildsOrRejects(IrGenerationConfig.defaults(), input);
    }

    private void assertBuildsOrRejects(IrGenerationConfig config, byte[] input) {
        try {
            assertFalse(print(IrGenerators.expressions(config).generate(input)).isEmpty());
        } catch (InputRejectedException expected) {
            // Some forms intentionally reject when the current scope cannot satisfy them.
        } catch (RuntimeException failure) {
            throw new AssertionError(
                    "generation failed for input " + Base64.getEncoder().encodeToString(input),
                    failure);
        }
    }

    private IrGenerationConfig configIgnoring(Set<ExpressionCategory> ignoredCategories) {
        var defaults = IrGenerationConfig.defaults();
        return new IrGenerationConfig(
                defaults.maximumTypeDepth(),
                defaults.maximumExpressionDepth(),
                defaults.maximumNodes(),
                defaults.maximumCollectionSize(),
                defaults.maximumStringBytes(),
                defaults.maximumIntegerBytes(),
                ignoredCategories);
    }

    private String print(TlaEx expression) {
        var buffer = new StringWriter();
        var printWriter = new PrintWriter(buffer);
        new PrettyWriter(printWriter, new TextLayout(80, 2), new TlaDeclAnnotator())
                .write(expression);
        printWriter.flush();
        return buffer.toString();
    }
}
