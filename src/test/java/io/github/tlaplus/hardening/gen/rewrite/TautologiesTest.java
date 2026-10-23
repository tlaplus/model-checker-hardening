package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
import io.github.tlaplus.hardening.gen.engine.OperandGenerator;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/** Edge guards drawn from a rule library (ADR 0023 §6). */
class TautologiesTest {
    private static final List<OperandGenerator.Name> SCOPE = List.of(
            new OperandGenerator.Name("x", TlaTypes.INT, OperandGenerator.Kind.STATE_VARIABLE),
            new OperandGenerator.Name("b", TlaTypes.BOOL, OperandGenerator.Kind.STATE_VARIABLE),
            new OperandGenerator.Name("S", TlaTypes.set(TlaTypes.INT), OperandGenerator.Kind.STATE_VARIABLE));

    /** The state-level rules among the fixtures, all of which can guard an edge. */
    private static final TlaOperDecl[] STATE_RULES = {plusZero(), addSub(), doubleNeg(), unionSelf(), commuteAnd(), ifAsAndOr()};

    @Test
    void onlyStateLevelFirstOrderRulesWithAWeightGuardAnEdge() {
        var rules = new TlaOperDecl[] {addSub(), unchangedPrime(), forallSwap(), doubleNeg(), unionSelf()};
        var library = RewriteLibrary.fromModule(module(rules), names(rules), Map.of("DoubleNeg", 0));
        assertEquals(List.of("AddSub", "UnionSelf"),
                new Tautologies(library).rules().stream().map(RewriteRule::name).toList());
    }

    @Test
    void aLibraryWithoutAGuardIsRejected() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> new Tautologies(library(unchangedPrime(), forallSwap())));
        assertTrue(failure.getMessage().startsWith("no rewrite rule can guard an edge"), failure.getMessage());
    }

    /** Two bytes index the weighted slots, as the rewriter's do: PlusZero has slot 0, DoubleNeg slots 1 and 2. */
    @Test
    void theIndexSelectsAmongTheWeightedSlotsOfTheRules() {
        var rules = new TlaOperDecl[] {plusZero(), doubleNeg()};
        var tautologies = new Tautologies(RewriteLibrary.fromModule(module(rules), names(rules), Map.of("DoubleNeg", 2)));
        assertEquals(TlaTypes.INT, TlaTypes.typeOf(sides(guard(tautologies, 0, 0)).getFirst()));
        assertEquals(TlaTypes.BOOL, TlaTypes.typeOf(sides(guard(tautologies, 0, 1)).getFirst()));
        assertEquals(TlaTypes.BOOL, TlaTypes.typeOf(sides(guard(tautologies, 0, 2)).getFirst()));
        assertEquals(TlaTypes.INT, TlaTypes.typeOf(sides(guard(tautologies, 0, 3)).getFirst()));
    }

    /**
     * A guard is a state predicate over the scope, and the same bytes draw the same guard. An
     * operand may prime a name under ENABLED, which keeps it a state predicate.
     */
    @Test
    void aGuardIsAnInstanceOfARuleFormulaOverTheScope() {
        var tautologies = new Tautologies(library(STATE_RULES));
        var random = new Random(0x7a5);
        for (var sample = 0; sample < 300; sample++) {
            var input = new byte[random.nextInt(64)];
            random.nextBytes(input);
            var guard = guard(tautologies, input);
            var oper = ((OperEx) guard).oper();
            assertTrue(oper == TlaOperators.EQ || oper == TlaOperators.EQUIV, TlaIrTestSupport.print(guard));
            assertEquals(TlaTypes.BOOL, TlaTypes.typeOf(guard));
            assertEquals(TlaIrTestSupport.IrLevel.STATE, TlaIrTestSupport.level(guard), TlaIrTestSupport.print(guard));
            assertTrue(Set.of("x", "b", "S").containsAll(IrNames.free(guard)), TlaIrTestSupport.print(guard));
            assertEquals(guard, guard(tautologies, input));
        }
    }

    /**
     * Guarded graphs keep every edge's shape around its guards, place at most two per edge, and
     * label their operands validly.
     */
    @Test
    void guardedGraphsConjoinAtMostMaximumGuardsAfterThePcTest() {
        var config = IrGenerationConfig.defaults();
        var graphs = IrGenerators.graphs(config, new Tautologies(library(STATE_RULES)));
        var random = new Random(0x3c1);
        var guards = 0;
        for (var sample = 0; sample < 300; sample++) {
            var input = new byte[16 + random.nextInt(496)];
            random.nextBytes(input);
            final GeneratedGraph graph;
            try {
                graph = graphs.generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            try {
                guards += checkEdges(graph, config.graphs().maximumGuards());
            } catch (AssertionError failure) {
                throw new AssertionError("failed for input " + Base64.getEncoder().encodeToString(input), failure);
            }
        }
        assertTrue(guards > 1000, "too few guards to be conclusive: " + guards);
    }

    /** Checks the conjuncts of every edge and returns how many guards they hold. */
    private static int checkEdges(GeneratedGraph graph, int maximumGuards) {
        var edges = graph.graph().edges();
        var disjuncts = TlaIrTestSupport.disjuncts(graph.spec().nextAction());
        assertEquals(edges.size(), disjuncts.size());
        var control = Set.of(GeneratedGraph.PC_VARIABLE, GeneratedSpec.STEP_VARIABLE);
        var count = 0;
        for (var index = 0; index < edges.size(); index++) {
            var conjuncts = TlaExpressions.arguments((OperEx) disjuncts.get(index));
            var source = edges.get(index).source();
            assertEquals("pc = " + source, TlaIrTestSupport.print(conjuncts.get(1)));
            var guards = conjuncts.subList(2, conjuncts.size() - 3);
            assertTrue(guards.size() <= maximumGuards, guards.toString());
            assertEquals("Node" + source, TlaIrTestSupport.print(conjuncts.get(conjuncts.size() - 3)));
            for (var guard : guards) {
                assertTrue(guard instanceof OperEx relation
                        && (relation.oper() == TlaOperators.EQ || relation.oper() == TlaOperators.EQUIV));
                assertTrue(java.util.Collections.disjoint(control, IrNames.free(guard)), TlaIrTestSupport.print(guard));
            }
            count += guards.size();
        }
        TlaIrTestSupport.assertLabelParameters(graph.spec().nextAction());
        return count;
    }

    private static TlaEx guard(Tautologies tautologies, int... bytes) {
        var input = new byte[bytes.length];
        for (var index = 0; index < bytes.length; index++) {
            input[index] = (byte) bytes[index];
        }
        return guard(tautologies, input);
    }

    private static TlaEx guard(Tautologies tautologies, byte[] input) {
        var operands = new OperandGenerator(IrGenerationConfig.defaults(), Set.of("x", "b", "S"));
        return new Draw(input).draw(tautologies.guard(operands, SCOPE).orElseThrow());
    }

    private static List<TlaEx> sides(TlaEx formula) {
        return TlaExpressions.arguments((OperEx) formula);
    }
}
