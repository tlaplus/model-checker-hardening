package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class RewriterTest {
    private static final TlaEx SUM = B.plus(B.integer(1), B.integer(2));

    @Test
    void exhaustedInputExploresTheOriginalAndRewritesNothing() {
        var rewrite = rewriter(library(plusZero())).rewriteExpression(SUM, new Draw(new byte[0]));
        assertEquals(Orientation.EXPLORE_ORIGINAL, rewrite.orientation());
        assertTrue(rewrite.isIdentity());
        assertEquals(SUM, rewrite.rewritten());
    }

    @Test
    void anOddMarkerRewritesTheNodeAndTheWalkReexaminesTheReplacement() {
        // orientation, then at the root: marker, two-byte index, marker, two-byte index, even marker.
        var once = rewrite(library(plusZero()), 1, 1, 0, 0);
        assertEquals(Orientation.EXPLORE_REWRITE, once.orientation());
        assertEquals(B.plus(SUM, B.integer(0)), once.rewritten());
        assertEquals(List.of("PlusZero"), once.appliedRules());
        var twice = rewrite(library(plusZero()), 0, 1, 0, 0, 1, 0, 0);
        assertEquals(B.plus(B.plus(SUM, B.integer(0)), B.integer(0)), twice.rewritten());
    }

    @Test
    void theLimitsBoundStackingAndRewritesPerBody() {
        var ones = new byte[64];
        java.util.Arrays.fill(ones, (byte) 1);
        var shallow = new Rewriter(library(plusZero()), IrGenerationConfig.defaults(), new RewriteLimits(16, 1))
                .rewriteExpression(B.integer(7), new Draw(ones));
        // 7 becomes 7 + 0 once; the copy of 7 continues its stack, while the new 0 may become 0 + 0.
        assertEquals(B.plus(B.integer(7), B.plus(B.integer(0), B.integer(0))), shallow.rewritten());
        assertEquals(List.of("PlusZero", "PlusZero"), shallow.appliedRules());
        var none = new Rewriter(library(plusZero()), IrGenerationConfig.defaults(), new RewriteLimits(0, 4))
                .rewriteExpression(SUM, new Draw(ones));
        assertTrue(none.isIdentity());
        var bounded = new Rewriter(library(plusZero()), IrGenerationConfig.defaults(), new RewriteLimits(3, 4))
                .rewriteExpression(SUM, new Draw(ones));
        assertEquals(3, bounded.appliedRules().size());
    }

    @Test
    void theIndexSelectsAmongTheWeightedSlotsOfTheApplicableRules() {
        var rules = new at.forsyte.apalache.tla.lir.TlaOperDecl[] {plusZero(), addSub()};
        var library = RewriteLibrary.fromModule(module(rules), names(rules), Map.of("AddSub", 2));
        assertEquals(List.of("PlusZero"), rewrite(library, 0, 1, 0, 0).appliedRules());
        var addSub = rewrite(library, 0, 1, 0, 1, 0);
        assertEquals(List.of("AddSub"), addSub.appliedRules());
        var sum = (OperEx) addSub.rewritten();
        assertEquals(TlaOperators.PLUS, sum.oper());
        assertEquals(TlaOperators.MINUS, ((OperEx) TlaExpressions.arguments(sum).get(1)).oper());
    }

    @Test
    void aNodeWhereNoRuleAppliesReadsNoByte() {
        var draw = new Draw(new byte[] {0, 1, 1, 1});
        var rewrite = rewriter(library(doubleNeg())).rewriteExpression(SUM, draw);
        assertTrue(rewrite.isIdentity());
        assertEquals(3, draw.remaining(), "only the orientation marker was read");
    }

    @Test
    void aPrimedNameIsNeverRewritten() {
        var x = B.name("x", TlaTypes.INT);
        var action = B.and(B.primeEq(x, B.integer(1)), B.gt(B.name("y", TlaTypes.INT), B.integer(0)));
        var random = new Random(3);
        for (var sample = 0; sample < 50; sample++) {
            var bytes = new byte[40];
            random.nextBytes(bytes);
            var rewritten = rewriter(library(plusZero())).rewriteExpression(action, new Draw(bytes)).rewritten();
            TlaExpressions.forEach(rewritten, node -> {
                if (node instanceof OperEx prime && prime.oper() == TlaOperators.PRIME) {
                    assertTrue(TlaExpressions.arguments(prime).getFirst() instanceof NameEx, rewritten.toString());
                }
            });
        }
    }

    @Test
    void isDeterministic() {
        var bytes = new byte[] {1, 1, 0, 1, 1, 7, 1, 0, 0, 1};
        var library = library(plusZero(), addSub(), doubleNeg());
        assertEquals(rewriter(library).rewriteExpression(SUM, new Draw(bytes)),
                rewriter(library).rewriteExpression(SUM, new Draw(bytes)));
    }

    @Test
    void thePayloadHeaderSlicesTheBaseAndClampsItsLength() {
        var input = MetamorphicPayload.encode(new byte[] {5, 6, 7}, new byte[] {8, 9});
        assertArrayEquals(new byte[] {0, 3, 5, 6, 7, 8, 9}, input);
        var parts = MetamorphicPayload.split(new Draw(input));
        assertEquals(3, parts.base().remaining());
        assertEquals(2, parts.rewrite().remaining());
        var clamped = MetamorphicPayload.split(new Draw(new byte[] {0x7f, 0, 1, 2}));
        assertEquals(2, clamped.base().remaining());
        assertEquals(0, clamped.rewrite().remaining());
    }

    private static Rewrite<TlaEx> rewrite(RewriteLibrary library, int... bytes) {
        var input = new byte[bytes.length];
        for (var index = 0; index < bytes.length; index++) {
            input[index] = (byte) bytes[index];
        }
        return rewriter(library).rewriteExpression(SUM, new Draw(input));
    }

    private static Rewriter rewriter(RewriteLibrary library) {
        return new Rewriter(library, IrGenerationConfig.defaults(), RewriteLimits.defaults());
    }
}
