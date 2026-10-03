package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.ir.IrNames;
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

    /** SANY rejects two labels of one name in a definition, so a copied operand is relabelled. */
    @Test
    void aRuleThatCopiesALabelledOperandGivesEveryCopyItsOwnLabel() {
        var labelled = B.label(B.enumSet(B.integer(1)), "label3");
        var rewrite = rewriter(library(unionSelf())).rewriteExpression(labelled, new Draw(new byte[] {0, 1, 0, 0}));
        assertEquals(List.of("UnionSelf"), rewrite.appliedRules());
        var copies = TlaExpressions.arguments((OperEx) rewrite.rewritten());
        assertEquals(labelled, copies.get(0));
        var names = IrNames.labels(rewrite.rewritten());
        assertEquals(2, names.size(), names.toString());
        assertTrue(names.contains("label3"));
    }

    /**
     * PrettyWriter moves the LET definitions of an operator's arguments in front of the
     * application, which nests copies of one definition, and SANY rejects a definition that
     * repeats an enclosing one. A copied operand therefore defines its own names.
     */
    @Test
    void aRuleThatCopiesALetDefinitionGivesEveryCopyItsOwnName() {
        var defined = B.letIn(B.enumSet(B.integer(1)), B.decl("LocalOp2", B.integer(1)));
        var rewrite = rewriter(library(unionSelf())).rewriteExpression(defined, new Draw(new byte[] {0, 1, 0, 0}));
        assertEquals(List.of("UnionSelf"), rewrite.appliedRules());
        var copies = TlaExpressions.arguments((OperEx) rewrite.rewritten());
        assertEquals(defined, copies.get(0));
        var names = new java.util.ArrayList<String>();
        TlaExpressions.forEach(rewrite.rewritten(), node -> {
            if (node instanceof LetInEx let) {
                TlaExpressions.localDeclarations(let).forEach(declaration -> names.add(declaration.name()));
            }
        });
        assertEquals(2, java.util.Set.copyOf(names).size(), names.toString());
    }

    /** TLA+ has no IF or equality of operators, so a rule of every type skips an operator argument. */
    @Test
    void noRuleAppliesAtAnOperator() {
        var x = B.name("x", ELEMENT);
        var ifSelf = rule("IfSelf", B.eql(x, B.ite(B.bool(true), B.name("x", ELEMENT), B.name("x", ELEMENT))),
                B.param("x", ELEMENT));
        var lambda = B.lambda("Lambda3", integer("accumulator1"),
                B.param("accumulator1", TlaTypes.INT), B.param("element2", TlaTypes.INT));
        var fold = B.foldSet(lambda, B.integer(0), B.enumSet(B.integer(1)));
        var random = new Random(5);
        var rewritten = 0;
        for (var sample = 0; sample < 200; sample++) {
            var bytes = new byte[32];
            random.nextBytes(bytes);
            var rewrite = rewriter(library(ifSelf)).rewriteExpression(fold, new Draw(bytes));
            rewritten += rewrite.isIdentity() ? 0 : 1;
            TlaExpressions.forEach(rewrite.rewritten(), node -> {
                if (node instanceof OperEx choice && choice.oper() == TlaOperators.IF_THEN_ELSE) {
                    assertTrue(!(TlaTypes.typeOf(choice) instanceof OperT1), rewrite.rewritten().toString());
                }
            });
        }
        assertTrue(rewritten > 20, "only " + rewritten + " of 200 inputs were rewritten");
    }

    /** LET definitions are not recursive, so an operand drawn in a definition cannot name it. */
    @Test
    void anOperandDrawnInALetDefinitionDoesNotNameIt() {
        var defined = B.letIn(B.plus(integer("LocalOp2"), B.integer(1)), B.decl("LocalOp2", B.integer(1)));
        var random = new Random(11);
        var drawn = 0;
        for (var sample = 0; sample < 200; sample++) {
            var bytes = new byte[64];
            random.nextBytes(bytes);
            var rewritten = rewriter(library(addSub())).rewriteExpression(defined, new Draw(bytes)).rewritten();
            var definitions = new int[1];
            TlaExpressions.forEach(rewritten, node -> {
                if (node instanceof LetInEx let) {
                    for (var declaration : TlaExpressions.localDeclarations(let)) {
                        assertTrue(!IrNames.free(declaration.body()).contains(declaration.name()), rewritten.toString());
                        definitions[0] += declaration.body().equals(B.integer(1)) ? 0 : 1;
                    }
                }
            });
            drawn += definitions[0];
        }
        assertTrue(drawn > 20, "only " + drawn + " definitions were rewritten");
    }

    @Test
    void theLimitsBoundStackingAndRewritesPerBody() {
        var ones = new byte[64];
        java.util.Arrays.fill(ones, (byte) 1);
        var shallow = new Rewriter(library(plusZero()), IrGenerationConfig.defaults(), new RewriteLimits(16, 1, 2))
                .rewriteExpression(B.integer(7), new Draw(ones));
        // 7 becomes 7 + 0 once; the copy of 7 continues its stack, while the new 0 may become 0 + 0.
        assertEquals(B.plus(B.integer(7), B.plus(B.integer(0), B.integer(0))), shallow.rewritten());
        assertEquals(List.of("PlusZero", "PlusZero"), shallow.appliedRules());
        var none = new Rewriter(library(plusZero()), IrGenerationConfig.defaults(), new RewriteLimits(0, 4, 2))
                .rewriteExpression(SUM, new Draw(ones));
        assertTrue(none.isIdentity());
        var bounded = new Rewriter(library(plusZero()), IrGenerationConfig.defaults(), new RewriteLimits(3, 4, 2))
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

    /**
     * A module's bodies are rewritten in a fixed order, which is part of the byte encoding: the
     * operators, then Init, the next-state action and the invariant. The property is not rewritten.
     */
    @Test
    void rewritesTheBodiesOfAModuleInOrder() {
        var x = B.name("var0", TlaTypes.INT);
        var step = B.name(io.github.tlaplus.hardening.gen.GeneratedSpec.STEP_VARIABLE, TlaTypes.INT);
        var op = B.decl("Op1", B.integer(1));
        var spec = new io.github.tlaplus.hardening.gen.GeneratedSpec(
                List.of(org.apalache_mc.tla.jir.TlaDeclarations.variable("var0", TlaTypes.INT),
                        org.apalache_mc.tla.jir.TlaDeclarations.variable("step", TlaTypes.INT)),
                List.<io.github.tlaplus.hardening.gen.GeneratedOperator>of(
                        new io.github.tlaplus.hardening.gen.GeneratedOperator.Auxiliary(op)),
                B.and(B.eql(x, B.integer(0)), B.eql(step, B.integer(0))),
                B.and(B.primeEq(B.name("var0", TlaTypes.INT), B.integer(2)), B.primeEq(step, B.integer(1))),
                B.bool(true), java.util.Optional.empty(), 3);
        // Orientation, then one rewrite at the root of Op1's body; every later marker is even.
        var rewrite = rewriter(library(plusZero())).rewriteSpec(spec, new Draw(new byte[] {0, 1, 0, 0}));
        assertEquals(List.of("PlusZero"), rewrite.appliedRules());
        assertEquals(B.plus(B.integer(1), B.integer(0)), rewrite.rewritten().operators().getFirst().declaration().body());
        assertEquals(spec.initPredicate(), rewrite.rewritten().initPredicate());
        assertEquals(spec.nextAction(), rewrite.rewritten().nextAction());
    }

    /** UnionSelf duplicates its set; repeated, it would double the body with every rewrite. */
    @Test
    void aBodyGrowsAtMostByTheGrowthFactor() {
        var ones = new byte[256];
        java.util.Arrays.fill(ones, (byte) 1);
        var set = B.enumSet(B.integer(1), B.integer(2), B.integer(3));
        var rewrite = new Rewriter(library(unionSelf()), IrGenerationConfig.defaults(), new RewriteLimits(64, 4, 2))
                .rewriteExpression(set, new Draw(ones));
        var nodes = new int[1];
        TlaExpressions.forEach(rewrite.rewritten(), ignored -> nodes[0]++);
        assertTrue(!rewrite.isIdentity());
        assertTrue(nodes[0] <= 4 * 2 + IrGenerationConfig.defaults().expressions().maximumNodes(),
                nodes[0] + " nodes after " + rewrite.appliedRules().size() + " rewrites");
    }

    /** Init assigns unprimed variables: var0 \in S must keep its variable and its position. */
    @Test
    void aRewriteKeepsTheAssignmentsOfInit() {
        var sets = TlaTypes.set(TlaTypes.INT);
        var x = B.name("var0", sets);
        var step = B.name(io.github.tlaplus.hardening.gen.GeneratedSpec.STEP_VARIABLE, TlaTypes.INT);
        var init = B.and(B.in(x, B.enumSet(B.enumSet(B.integer(1)), B.enumSet(B.integer(2)))),
                B.eql(step, B.integer(0)));
        var spec = new io.github.tlaplus.hardening.gen.GeneratedSpec(
                List.of(org.apalache_mc.tla.jir.TlaDeclarations.variable("var0", sets),
                        org.apalache_mc.tla.jir.TlaDeclarations.variable("step", TlaTypes.INT)),
                List.of(), init,
                B.and(B.primeEq(B.name("var0", sets), B.name("var0", sets)), B.primeEq(step, B.integer(1))),
                B.bool(true), java.util.Optional.empty(), 3);
        var ones = new byte[256];
        java.util.Arrays.fill(ones, (byte) 1);
        var rewritten = rewriter(library(unionSelf(), doubleNeg())).rewriteSpec(spec, new Draw(ones)).rewritten();
        var conjuncts = TlaExpressions.arguments((OperEx) rewritten.initPredicate());
        assertEquals(TlaOperators.SET_IN, ((OperEx) conjuncts.get(0)).oper(), rewritten.initPredicate().toString());
        assertEquals(x, TlaExpressions.arguments((OperEx) conjuncts.get(0)).getFirst());
        assertEquals(B.eql(step, B.integer(0)), conjuncts.get(1));
    }

    @Test
    void isDeterministic() {
        var bytes = new byte[] {1, 1, 0, 1, 1, 7, 1, 0, 0, 1};
        var library = library(plusZero(), addSub(), doubleNeg());
        assertEquals(rewriter(library).rewriteExpression(SUM, new Draw(bytes)),
                rewriter(library).rewriteExpression(SUM, new Draw(bytes)));
    }

    @Test
    void thePayloadHeaderSlicesTheBaseModuloTheInput() {
        var input = MetamorphicPayload.encode(new byte[] {5, 6, 7}, new byte[] {8, 9});
        assertArrayEquals(new byte[] {0, 3, 5, 6, 7, 8, 9}, input);
        var parts = MetamorphicPayload.split(new Draw(input));
        assertEquals(3, parts.base().remaining());
        assertEquals(2, parts.rewrite().remaining());
        // 0x7f00 = 32512 = 3 * 10837 + 1: one base byte of two.
        var reduced = MetamorphicPayload.split(new Draw(new byte[] {0x7f, 0, 1, 2}));
        assertEquals(1, reduced.base().remaining());
        assertEquals(1, reduced.rewrite().remaining());
        var whole = MetamorphicPayload.split(new Draw(new byte[] {0, 2, 1, 2}));
        assertEquals(2, whole.base().remaining());
        assertEquals(0, whole.rewrite().remaining());
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
