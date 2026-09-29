package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.CheckerProfile;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLimits;
import io.github.tlaplus.hardening.gen.rewrite.Rewriter;
import io.github.tlaplus.hardening.workflow.library.RuleLibraryPreparation;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetamorphicShrinkerTest {
    @Test
    void clearsEveryRewriteBitTheViolationDoesNotNeed() throws Exception {
        var base = new byte[] {7, 7, 7};
        var input = MetamorphicPayload.encode(base, new byte[] {1, 3, 5, 7});
        var needed = MetamorphicPayload.rewriteOffset(input) + 2;
        var result = MetamorphicShrinker.shrink(input, candidate -> (candidate[needed] & 1) == 1);

        assertArrayEquals(MetamorphicPayload.encode(base, new byte[] {0, 2, 5, 6}), result.input());
        assertEquals(3, result.cleared());
    }

    /** A base that violates the relation with itself is returned unchanged, without shrinking. */
    @Test
    void returnsTheInputUnchangedWhenTheBaseAloneViolatesTheRelation() throws Exception {
        var input = MetamorphicPayload.encode(new byte[] {7}, new byte[] {1, 3});
        var result = MetamorphicShrinker.shrink(input, candidate -> {
            throw new AssertionError("shrinking must not start");
        }, candidate -> true);

        assertTrue(result.unrewritten());
        assertArrayEquals(input, result.input());
        assertEquals(1, result.checks());
        assertEquals(0, result.cleared());
    }

    /**
     * With the wrong rule {@code x = x + 1}, one rewrite of an integer literal already violates the
     * relation, so TLC keeps a counterexample after the shrinker clears the second rewrite.
     */
    @Test
    void shrinksACounterexampleOfAWrongRuleToOneRewrite(@TempDir Path directory) throws Exception {
        var rules = Files.createDirectory(directory.resolve("rules"));
        Files.writeString(rules.resolve("Wrong.tla"),
                "---- MODULE Wrong ----\nEXTENDS Integers\nPlusOne(x) == x = x + 1\n====\n");
        var library = RuleLibraryPreparation.prepare(
                new MetamorphicConfig(Optional.of(new MetamorphicConfig.RuleModule("Wrong", List.of(rules))),
                        Map.of(), RewriteLimits.defaults(), MetamorphicConfig.Adoption.defaults()),
                CheckerProfile.APALACHE.defaults()).library();
        var decoders = SpecDecoders.metamorphic(IrGenerationConfig.defaults(),
                new Rewriter(library, IrGenerationConfig.defaults(), RewriteLimits.defaults()));
        // Orientation, then two stacked rewrites at the root: marker, index, marker, index.
        var rewrite = new byte[] {0, 1, 0, 0, 1, 0, 0};
        byte[] input = null;
        for (var base = 0; base < 256 && input == null; base++) {
            var candidate = MetamorphicPayload.encode(new byte[] {(byte) base}, rewrite);
            try {
                if (decoders.decode(new CorpusInput(InputKind.EXPRESSION, candidate)).rewrite().orElseThrow()
                        .appliedRules().size() == 2) {
                    input = candidate;
                }
            } catch (InputRejectedException notAnInteger) {
                // The base is not an integer, so the rule does not apply.
            }
        }
        assertTrue(input != null, "no one-byte base decodes to an integer");

        var backend = new TlcCheckerBackend(new CheckerStageConfig(10, 60, 512, 1), 1,
                Files.createDirectory(directory.resolve("tlc")), List.of());
        try (var worker = backend.startWorker()) {
            var violation = MetamorphicShrinker.violation(
                    decoders, InputKind.EXPRESSION, List.of(new MetamorphicShrinker.Checker(backend, worker)));
            var unrewritten = MetamorphicShrinker.unrewritten(
                    decoders, InputKind.EXPRESSION, List.of(new MetamorphicShrinker.Checker(backend, worker)));
            assertTrue(violation.holds(input));
            assertFalse(unrewritten.holds(input), "TLC evaluates both copies of the base alike");
            var result = MetamorphicShrinker.shrink(input, violation, unrewritten);

            var after = decoders.decode(new CorpusInput(InputKind.EXPRESSION, result.input())).rewrite().orElseThrow();
            assertEquals(List.of("PlusOne"), after.appliedRules());
            assertTrue(violation.holds(result.input()));
            assertFalse(result.unrewritten());
        }
    }
}
