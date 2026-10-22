package io.github.tlaplus.hardening.gen;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.actionOperatorBodies;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.collectAssignedVars;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.collectFreeReads;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.disjuncts;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.variableNames;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.graph.TransitionGraph;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.junit.jupiter.api.Test;

/** The graph decoder of ADR 0023 §2–§4. */
class IrGraphGeneratorsTest {
    private static final String PC = GeneratedGraph.PC_VARIABLE;
    private static final String STEP = GeneratedSpec.STEP_VARIABLE;

    @Test
    void emptyInputProducesOneNodeWithoutEdgesWhoseInitialStateViolatesTheInvariant() {
        var graph = generate(new byte[0]);
        var spec = graph.spec();

        assertEquals(new TransitionGraph(List.of(List.of())), graph.graph());
        assertEquals(0, graph.target());
        assertEquals(OptionalInt.of(0), graph.targetDistance());
        assertEquals(List.of("var0", PC, STEP), variableNames(spec));
        assertEquals(List.of("Node0"), spec.operators().stream().map(operator -> operator.declaration().name()).toList());
        assertEquals("var0' = FALSE", print(spec.operators().getFirst().declaration().body()));
        assertEquals("var0 = FALSE /\\ pc = 0 /\\ step = 0", normalized(spec.initPredicate()));
        assertEquals("FALSE", print(spec.nextAction()));
        assertEquals("pc /= 0", normalized(spec.invariant()));
        assertTrue(spec.property().isEmpty());
        assertEquals(ModuleLimits.DEFAULT_MAXIMUM_STEPS, spec.stepBound());
    }

    @Test
    void theHeaderSelectsTheNodeCountAndTheTarget() {
        // N = 1 + 5 mod 6 and k = 1 + 3 mod 5: the target is never the initial node of a larger graph.
        var graph = generate(new byte[] {5, 3});

        assertEquals(6, graph.graph().nodeCount());
        assertEquals(4, graph.target());
        assertEquals(OptionalInt.empty(), graph.targetDistance(), "no edge leads to node 4");
        assertEquals(6, graph.spec().operators().size());
    }

    /** Two seeded inputs pin the graph encoding: header, sections and successor lists. */
    @Test
    void pinnedInputsDecodeToTheirGraphs() {
        var reachable = generate(Base64.getDecoder().decode(
                "AX5tGn3sivVUTbYOOklQyuKLkESPHHAXakK+EeL74AE1NxKHHRuI9b7A04TeV5RfuNI2nD2soVo/nUcfWhl7xyuP0pMMOum/4ceQcGZoo1og0qAM80SLEo0wA27VjJvI"));
        assertEquals(new TransitionGraph(List.of(List.of(1, 0), List.of(0))), reachable.graph());
        assertEquals(1, reachable.target());
        assertEquals(OptionalInt.of(1), reachable.targetDistance());
        assertEquals(List.of("var0", PC, STEP), variableNames(reachable.spec()));

        var unreachable = generate(Base64.getDecoder().decode(
                "Faa55CsU1wJZh9uAZ3wRIcSXZRWCar3X+UtKBiqnWh+4lMEBXkP/rHTg234cpoEwI5+pjALcHEUak0I2FbAtLu5qehoQjVXitNNig+P4kb8SOKfTTjgNYP0H0eG2XtGz"));
        assertEquals(new TransitionGraph(List.of(List.of(), List.of(2, 0), List.of(0), List.of())), unreachable.graph());
        assertEquals(2, unreachable.target());
        assertEquals(OptionalInt.empty(), unreachable.targetDistance());
        assertEquals(List.of("var0", "var1", PC, STEP), variableNames(unreachable.spec()));
    }

    @Test
    void generationIsDeterministic() {
        var input = new byte[] {7, 1, 3, 1, 9, 0, 5, 12, 1, 8, 0, 33, 91, 4, 17, 1, 1, 2, 1, 0};

        assertEquals(render(generate(input)), render(generate(input)));
    }

    @Test
    void everyEdgeIsOneDisjunctThatRunsItsSourceNode() {
        forEachGeneratedGraph(graph -> {
            var bound = graph.spec().stepBound();
            var expected = graph.graph().edges().stream()
                    .map(edge -> "step < " + bound + " /\\ pc = " + edge.source() + " /\\ Node" + edge.source()
                            + " /\\ pc' = " + edge.target() + " /\\ step' = step + 1")
                    .toList();
            if (expected.isEmpty()) {
                assertEquals("FALSE", print(graph.spec().nextAction()));
                return;
            }
            var actual = disjuncts(graph.spec().nextAction()).stream()
                    .map(disjunct -> normalized(disjunct).replaceAll("^\\((.*)\\)$", "$1"))
                    .toList();
            assertEquals(expected, actual);
        });
    }

    @Test
    void everyNodeAssignsExactValuesAndAccountsForEveryDataVariable() {
        forEachGeneratedGraph(graph -> {
            var spec = graph.spec();
            var data = new LinkedHashSet<>(variableNames(spec).subList(0, variableNames(spec).size() - 2));
            var bodies = actionOperatorBodies(spec);
            assertEquals(graph.graph().nodeCount(), bodies.size());
            for (var body : bodies.values()) {
                var account = collectAssignedVars(body, data, bodies);
                assertEquals(data, new LinkedHashSet<>(account), print(body));
                assertEquals(data.size(), account.size(), print(body));
                assertFalse(assignsByMembership(body), "a node action draws a membership: " + print(body));
            }
            for (var operator : spec.operators()) {
                if (operator instanceof GeneratedActionOperator action) {
                    assertEquals(data, new LinkedHashSet<>(action.effect().variables()));
                }
            }
        });
    }

    @Test
    void rightHandSidesReadNeitherPcNorStep() {
        forEachGeneratedGraph(graph -> {
            var control = Set.of(PC, STEP);
            for (var body : actionOperatorBodies(graph.spec()).values()) {
                var reads = new ArrayList<String>();
                collectFreeReads(body, control, reads, false);
                assertTrue(reads.isEmpty(), "a node action reads " + reads + ": " + print(body));
            }
            var initReads = new ArrayList<String>();
            collectFreeReads(graph.spec().initPredicate(), new HashSet<>(variableNames(graph.spec())), initReads, true);
            assertTrue(initReads.isEmpty(), "Init reads " + initReads);
        });
    }

    @Test
    void initAssignsExactValuesAndStartsAtNodeZero() {
        forEachGeneratedGraph(graph -> {
            var init = graph.spec().initPredicate();
            var conjuncts = TlaExpressions.arguments((OperEx) init);
            assertEquals(variableNames(graph.spec()).size(), conjuncts.size(), print(init));
            for (var conjunct : conjuncts) {
                assertEquals(TlaOperators.EQ, ((OperEx) conjunct).oper(), print(conjunct));
            }
            assertTrue(normalized(init).endsWith("pc = 0 /\\ step = 0"), print(init));
            assertEquals("pc /= " + graph.target(), normalized(graph.spec().invariant()));
        });
    }

    @Test
    void theTargetIsTheInitialNodeOnlyInASingleNodeGraph() {
        forEachGeneratedGraph(graph -> assertEquals(graph.graph().nodeCount() == 1, graph.target() == 0));
    }

    @Test
    void theGraphRespectsItsLimitsAndTheTargetDistanceItsBound() {
        var reachable = new int[1];
        var unreachable = new int[1];
        forEachGeneratedGraph(graph -> {
            var limits = GraphLimits.defaults();
            assertTrue(graph.graph().nodeCount() <= limits.maximumNodes());
            graph.graph().successors().forEach(targets -> assertTrue(targets.size() <= limits.maximumSuccessors()));
            var distance = graph.graph().distance(graph.target());
            var withinBound = distance.isPresent() && distance.getAsInt() <= graph.spec().stepBound();
            assertEquals(withinBound ? distance : OptionalInt.empty(), graph.targetDistance());
            (withinBound ? reachable : unreachable)[0]++;
        });
        assertTrue(reachable[0] > 50, "reachable targets: " + reachable[0]);
        assertTrue(unreachable[0] > 50, "unreachable targets: " + unreachable[0]);
    }

    @Test
    void theStepBoundCutsOffDistantTargets() {
        var config = IrGenerationConfig.defaults().withModuleLimits(new ModuleLimits(
                1, 0, ActionLimits.defaults(), 0, 0));
        var random = new Random(0xb0d);
        for (var sample = 0; sample < 200; sample++) {
            var input = new byte[random.nextInt(8, 128)];
            random.nextBytes(input);
            var graph = IrGenerators.graphs(config).generate(input);
            assertEquals(graph.target() == 0 ? OptionalInt.of(0) : OptionalInt.empty(), graph.targetDistance());
        }
    }

    @Test
    void graphLimitsBoundNodesAndSuccessors() {
        var single = IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(1, 3));
        var noEdges = IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(6, 0));
        var random = new Random(0x11);
        for (var sample = 0; sample < 200; sample++) {
            var input = new byte[random.nextInt(256)];
            random.nextBytes(input);
            assertEquals(1, IrGenerators.graphs(single).generate(input).graph().nodeCount());
            assertTrue(IrGenerators.graphs(noEdges).generate(input).graph().edges().isEmpty());
        }
    }

    @Test
    void shortAndAdversarialInputsBuildCleanly() {
        for (var first = 0; first < 256; first++) {
            assertBuilds(IrGenerationConfig.defaults(), new byte[] {(byte) first});
            for (var second = 0; second < 256; second += 17) {
                assertBuilds(IrGenerationConfig.defaults(), new byte[] {(byte) first, (byte) second});
            }
        }
        var random = new Random(0x9a7f);
        for (var sample = 0; sample < 1000; sample++) {
            var input = new byte[random.nextInt(512)];
            random.nextBytes(input);
            assertBuilds(IrGenerationConfig.defaults(), input);
        }
        var saturated = new byte[8192];
        java.util.Arrays.fill(saturated, (byte) 0xff);
        assertBuilds(IrGenerationConfig.defaults(), saturated);
    }

    @Test
    void everyCategoryFilterBuildsAdversarialInputsCleanly() {
        var random = new Random(0x6a7e);
        for (var category : ExpressionCategory.values()) {
            if (!category.isIgnorable()) {
                continue;
            }
            var config = IrGenerationConfig.defaults().ignoring(category);
            for (var sample = 0; sample < 64; sample++) {
                var input = new byte[random.nextInt(257)];
                random.nextBytes(input);
                assertBuilds(config, input);
            }
        }
        assertBuilds(IrGenerationConfig.defaults().withIgnoredCategories(Set.of()), new byte[0]);
    }

    /** Runs a check over a spread of generated graphs, skipping the inputs that reject. */
    private void forEachGeneratedGraph(Consumer<GeneratedGraph> check) {
        var random = new Random(0x96a9L);
        var checked = 0;
        for (var sample = 0; sample < 600; sample++) {
            var input = new byte[16 + random.nextInt(496)];
            random.nextBytes(input);
            final GeneratedGraph graph;
            try {
                graph = generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            try {
                check.accept(graph);
            } catch (AssertionError failure) {
                throw new AssertionError("failed for input " + Base64.getEncoder().encodeToString(input), failure);
            }
            checked++;
        }
        assertTrue(checked > 300, "too few inputs were admitted to be conclusive: " + checked);
    }

    private static void assertBuilds(IrGenerationConfig config, byte[] input) {
        try {
            var graph = IrGenerators.graphs(config).generate(input);
            assertFalse(render(graph).isEmpty());
            graph.spec().operators().forEach(operator ->
                    TlaIrTestSupport.assertLabelParameters(operator.declaration().body()));
            graph.spec().generated().forEach(TlaIrTestSupport::assertLabelParameters);
        } catch (InputRejectedException expected) {
            // Some forms intentionally reject when the current scope cannot satisfy them.
        } catch (RuntimeException failure) {
            throw new AssertionError("generation failed for input " + Base64.getEncoder().encodeToString(input), failure);
        }
    }

    /** Whether some conjunct of {@code expression} assigns a primed variable by membership. */
    private static boolean assignsByMembership(TlaEx expression) {
        return normalized(expression).matches(".*\\w' \\\\in .*");
    }

    private static GeneratedGraph generate(byte[] input) {
        return IrGenerators.graphs(IrGenerationConfig.defaults()).generate(input);
    }

    private static String normalized(TlaEx expression) {
        return print(expression).replaceAll("\\s+", " ").trim();
    }

    private static String render(GeneratedGraph graph) {
        var text = new StringBuilder(graph.graph() + " target " + graph.target() + "\n");
        graph.spec().variables().forEach(variable -> text.append(variable.name()).append('\n'));
        graph.spec().operators().forEach(operator -> text.append(operator.declaration().name()).append(" == ")
                .append(print(operator.declaration().body())).append('\n'));
        graph.spec().generated().forEach(expression -> text.append(print(expression)).append('\n'));
        return text.toString();
    }
}
