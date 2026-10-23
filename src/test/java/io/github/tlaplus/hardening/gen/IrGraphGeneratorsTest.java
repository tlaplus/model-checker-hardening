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
import io.github.tlaplus.hardening.gen.engine.EdgeGuards;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.graph.TransitionGraph;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/** The graph decoder of ADR 0023 §2–§4. */
class IrGraphGeneratorsTest {
    private static final String PC = GeneratedGraph.PC_VARIABLE;
    private static final String STEP = GeneratedSpec.STEP_VARIABLE;

    /** Exhausted input decodes to the shortest spine, whose target lies exactly at the bound. */
    @Test
    void emptyInputProducesTheMinimumSpineWithATargetAtTheBound() {
        var graph = generate(new byte[0]);
        var spec = graph.spec();

        assertEquals(GraphLimits.DEFAULT_MINIMUM_NODES, graph.graph().nodeCount());
        for (var node = 0; node < graph.graph().nodeCount(); node++) {
            assertEquals(node + 1 < graph.graph().nodeCount() ? List.of(node + 1) : List.of(),
                    graph.graph().successors().get(node));
        }
        assertEquals(10, graph.target());
        assertEquals(10, spec.stepBound());
        assertEquals(OptionalInt.of(10), graph.targetDistance());
        assertEquals(List.of("var0", PC, STEP), variableNames(spec));
        assertEquals("var0' = FALSE", print(spec.operators().getFirst().declaration().body()));
        assertEquals("var0 = FALSE /\\ pc = 0 /\\ step = 0", normalized(spec.initPredicate()));
        assertEquals("step < 10 /\\ pc = 0 /\\ Node0 /\\ pc' = 1 /\\ step' = step + 1",
                normalized(disjuncts(spec.nextAction()).getFirst()));
        assertEquals("pc /= 10", normalized(spec.invariant()));
        assertTrue(spec.property().isEmpty());
    }

    /** N = 20 + 12, k = 16 + 15 and B = 16 + 0: the far end of the spine lies past the bound. */
    @Test
    void theHeaderSelectsTheNodeCountTheTargetAndTheBound() {
        var graph = generate(new byte[] {12, 15, 0});

        assertEquals(32, graph.graph().nodeCount());
        assertEquals(31, graph.target());
        assertEquals(16, graph.spec().stepBound());
        assertEquals(OptionalInt.of(31), graph.graph().distance(31));
        assertEquals(OptionalInt.empty(), graph.targetDistance(), "the target lies past the bound");
    }

    /**
     * Two seeded inputs pin the graph encoding: header, sections and successor lists. The edge
     * section is read from node 0 on, so their extra edges leave the first nodes.
     */
    @Test
    void pinnedInputsDecodeToTheirGraphs() {
        var reachable = generate(Base64.getDecoder().decode(
                "twK3Z5oy8TbTkAb9wYuzFHMpwlzGtOkJ9TTY0FCUS35DEyVP5mzis4LBJFUQf8gfCFik71m/Q/vpvjaCRKfi+lnHO/pwU2NurcJDhHleeHrLQ6pgZIe/QkQG2q6SA/hf"));
        assertEquals(spineWith(21, Map.of(0, List.of(2), 3, List.of(0), 4, List.of(2))), reachable.graph());
        assertEquals(13, reachable.target());
        assertEquals(17, reachable.spec().stepBound());
        assertEquals(OptionalInt.of(12), reachable.targetDistance(), "0 -> 2 skips a spine edge");
        assertEquals(List.of("var0", PC, STEP), variableNames(reachable.spec()));

        var unreachable = generate(Base64.getDecoder().decode(
                "xuMyj4M+XP3YSExXCbAzm6jCGPK2ytiUY96SNVtDZ3khO3h1N7IjxkxthXSDmUWHCnrEoQ/XwRjAYctU4yxPsR8paNs/wATtZyKHe2rznVW+dN9tOCb938gVvH0qgl33"));
        assertEquals(spineWith(23, Map.of(0, List.of(0), 3, List.of(1), 4, List.of(0))), unreachable.graph());
        assertEquals(19, unreachable.target());
        assertEquals(13, unreachable.spec().stepBound());
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
            assertFalse(expected.isEmpty(), "the spine leaves node 0");
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

    /** Every node but the last has its spine successor first; extra edges reach at most two nodes ahead. */
    @Test
    void everyGraphHasASpineAndShortForwardEdges() {
        forEachGeneratedGraph(graph -> {
            var successors = graph.graph().successors();
            var limits = GraphLimits.defaults();
            assertTrue(successors.size() >= limits.minimumNodes() && successors.size() <= limits.maximumNodes());
            for (var node = 0; node < successors.size(); node++) {
                var targets = successors.get(node);
                var spine = node + 1 < successors.size() ? 1 : 0;
                if (spine == 1) {
                    assertEquals(node + 1, targets.getFirst());
                }
                assertTrue(targets.size() - spine <= limits.maximumExtraSuccessors(), targets.toString());
                for (var target : targets) {
                    assertTrue(target <= node + 2, node + " -> " + target);
                }
                assertTrue(graph.graph().distance(node).orElseThrow() <= node, "node " + node + " is off the spine");
            }
        });
    }

    /**
     * The target lies in the upper half of the spine and the bound between half the spine and its
     * length, so both outcomes occur, and many counterexamples are 20 or more steps long.
     */
    @Test
    void theTargetAndTheBoundSplitTheOutcomes() {
        var reachable = new int[1];
        var unreachable = new int[1];
        var deep = new int[1];
        forEachGeneratedGraph(graph -> {
            var nodes = graph.graph().nodeCount();
            assertTrue(graph.target() >= (nodes + 1) / 2 && graph.target() < nodes, "target " + graph.target());
            var bound = graph.spec().stepBound();
            assertTrue(bound >= nodes / 2 && bound < nodes, "bound " + bound);
            var distance = graph.graph().distance(graph.target());
            var withinBound = distance.getAsInt() <= bound;
            assertEquals(withinBound ? distance : OptionalInt.empty(), graph.targetDistance());
            (withinBound ? reachable : unreachable)[0]++;
            deep[0] += withinBound && distance.getAsInt() >= 20 ? 1 : 0;
        });
        assertTrue(reachable[0] > 150, "reachable targets: " + reachable[0]);
        assertTrue(unreachable[0] > 150, "unreachable targets: " + unreachable[0]);
        assertTrue(deep[0] > 30, "counterexamples of 20 or more steps: " + deep[0]);
    }

    @Test
    void theStepCapCutsOffTheUpperHalfOfTheSpine() {
        var config = IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(20, 32, 2, 5, 2));
        var random = new Random(0xb0d);
        for (var sample = 0; sample < 200; sample++) {
            var input = new byte[random.nextInt(8, 256)];
            random.nextBytes(input);
            var graph = IrGenerators.graphs(config).generate(input);
            assertEquals(5, graph.spec().stepBound());
            var distance = graph.graph().distance(graph.target()).getAsInt();
            assertEquals(distance <= 5, graph.targetDistance().isPresent(), "distance " + distance);
        }
    }

    @Test
    void graphLimitsBoundNodesAndExtraSuccessors() {
        var single = IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(1, 1, 3, 32, 2));
        var spineOnly = IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(4, 40, 0, 40, 2));
        var random = new Random(0x11);
        for (var sample = 0; sample < 200; sample++) {
            var input = new byte[random.nextInt(256)];
            random.nextBytes(input);
            var one = IrGenerators.graphs(single).generate(input);
            assertEquals(1, one.graph().nodeCount());
            assertEquals(List.of(List.of(0)), one.graph().successors(), "a one-node spine is a self-loop");
            assertEquals(OptionalInt.of(0), one.targetDistance());
            var chain = IrGenerators.graphs(spineOnly).generate(input);
            assertEquals(chain.graph().nodeCount() - 1, chain.graph().edges().size());
            assertEquals(OptionalInt.of(chain.target()), chain.graph().distance(chain.target()));
        }
    }

    /**
     * The edge and node sections are read from node 0 on, so a short input gives extra edges and
     * actions to the first nodes, where every path starts, rather than every byte to the last node.
     */
    @Test
    void shortInputsSpendTheirBytesOnTheFirstNodes() {
        var random = new Random(0x5a0);
        var early = 0;
        var last = 0;
        var richerFirst = 0;
        var richerLast = 0;
        for (var sample = 0; sample < 300; sample++) {
            var input = new byte[16 + random.nextInt(112)];
            random.nextBytes(input);
            var graph = generate(input);
            var successors = graph.graph().successors();
            var nodes = successors.size();
            for (var node = 0; node < nodes; node++) {
                var extra = successors.get(node).size() - (node + 1 < nodes ? 1 : 0);
                if (node < nodes / 2) {
                    early += extra;
                } else if (node == nodes - 1) {
                    last += extra;
                }
            }
            var bodies = actionOperatorBodies(graph.spec());
            var first = print(bodies.get("Node0")).length();
            var final_ = print(bodies.get("Node" + (nodes - 1))).length();
            richerFirst += first > final_ ? 1 : 0;
            richerLast += final_ > first ? 1 : 0;
        }
        assertTrue(early > 10 * Math.max(last, 1), "extra edges of the first half: " + early + ", of the last node: " + last);
        assertTrue(richerFirst > 3 * richerLast, "Node0 richer: " + richerFirst + ", last node richer: " + richerLast);
    }

    /** A guard source that draws any Boolean operand: the engine places it as it places a tautology. */
    private static final EdgeGuards BOOLEAN_GUARDS =
            (operands, scope) -> Optional.of(operands.operand(TlaTypes.BOOL, scope, false));

    /**
     * Each edge conjoins at most max_guards guards between its pc test and its node action, and a
     * guard reads what a node action reads: neither pc nor step.
     */
    @Test
    void guardsFollowThePcTestOfTheirEdgeAndReadNeitherPcNorStep() {
        var config = IrGenerationConfig.defaults();
        var random = new Random(0x6a2d);
        var guards = 0;
        for (var sample = 0; sample < 300; sample++) {
            var input = new byte[16 + random.nextInt(496)];
            random.nextBytes(input);
            final GeneratedGraph graph;
            try {
                graph = IrGenerators.graphs(config, BOOLEAN_GUARDS).generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var edges = graph.graph().edges();
            var disjuncts = disjuncts(graph.spec().nextAction());
            assertEquals(edges.size(), disjuncts.size());
            for (var index = 0; index < edges.size(); index++) {
                var conjuncts = TlaExpressions.arguments((OperEx) disjuncts.get(index));
                var edge = edges.get(index);
                assertEquals("pc = " + edge.source(), print(conjuncts.get(1)));
                assertEquals("Node" + edge.source(), print(conjuncts.get(conjuncts.size() - 3)));
                var edgeGuards = conjuncts.subList(2, conjuncts.size() - 3);
                assertTrue(edgeGuards.size() <= config.graphs().maximumGuards(), edgeGuards.toString());
                for (var guard : edgeGuards) {
                    var reads = new ArrayList<String>();
                    collectFreeReads(guard, Set.of(PC, STEP), reads, false);
                    assertTrue(reads.isEmpty(), "a guard reads " + reads + ": " + print(guard));
                }
                guards += edgeGuards.size();
            }
            TlaIrTestSupport.assertLabelParameters(graph.spec().nextAction());
        }
        assertTrue(guards > 1000, "too few guards to be conclusive: " + guards);
    }

    /**
     * The guard section is one cursor in edge order, so a short input guards the edges leaving
     * node 0, where every path to the target starts, rather than those of the last node.
     */
    @Test
    void guardsAreReadInEdgeOrderFromNodeZero() {
        var graphs = IrGenerators.graphs(IrGenerationConfig.defaults(), BOOLEAN_GUARDS);
        var random = new Random(0x0e0);
        var first = 0;
        var last = 0;
        var samples = 0;
        for (var sample = 0; sample < 300; sample++) {
            var input = new byte[16 + random.nextInt(112)];
            random.nextBytes(input);
            var disjuncts = disjuncts(graphs.generate(input).spec().nextAction());
            first += TlaExpressions.arguments((OperEx) disjuncts.getFirst()).size() > 5 ? 1 : 0;
            last += TlaExpressions.arguments((OperEx) disjuncts.getLast()).size() > 5 ? 1 : 0;
            samples++;
        }
        // The first list marker starts a guard on about half the inputs.
        assertTrue(first > samples / 3, "edges leaving node 0 with a guard: " + first + " of " + samples);
        assertTrue(last < first / 4, "edges of the last node with a guard: " + last + ", of node 0: " + first);
    }

    /** With max_guards = 0 the guard section still takes its share, but no edge has a guard. */
    @Test
    void noEdgeHasAGuardUnderAZeroMaximum() {
        var config = IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(20, 32, 2, 32, 0));
        var random = new Random(0x0a);
        for (var sample = 0; sample < 50; sample++) {
            var input = new byte[16 + random.nextInt(496)];
            random.nextBytes(input);
            var graph = IrGenerators.graphs(config, BOOLEAN_GUARDS).generate(input);
            for (var disjunct : disjuncts(graph.spec().nextAction())) {
                assertEquals(5, TlaExpressions.arguments((OperEx) disjunct).size(), print(disjunct));
            }
        }
    }

    @Test
    void guardedGenerationIsDeterministic() {
        var input = new byte[] {7, 1, 3, 1, 9, 0, 5, 12, 1, 8, 0, 33, 91, 4, 17, 1, 1, 2, 1, 0, 44, 2, 9, 1, 1, 7};
        var graphs = IrGenerators.graphs(IrGenerationConfig.defaults(), BOOLEAN_GUARDS);
        assertEquals(render(graphs.generate(input)), render(graphs.generate(input)));
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

    /** A spine of {@code nodes} nodes where each node of {@code extra} has those successors after its spine successor. */
    private static TransitionGraph spineWith(int nodes, Map<Integer, List<Integer>> extra) {
        var successors = new ArrayList<List<Integer>>();
        for (var u = 0; u < nodes; u++) {
            var targets = new ArrayList<Integer>();
            if (u + 1 < nodes) {
                targets.add(u + 1);
            }
            extra.getOrDefault(u, List.of()).stream().filter(target -> !targets.contains(target)).forEach(targets::add);
            successors.add(targets);
        }
        return new TransitionGraph(successors);
    }

    private static GeneratedGraph generate(byte[] input) {
        return IrGenerators.graphs(IrGenerationConfig.defaults()).generate(input);
    }

    /** The printed expression with whitespace collapsed and every conjunction spaced alike. */
    private static String normalized(TlaEx expression) {
        return print(expression).replaceAll("\\s+", " ").replaceAll(" ?/\\\\ ?", " /\\\\ ").trim();
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
