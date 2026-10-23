package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.ActionEffect;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.GeneratedActionOperator;
import io.github.tlaplus.hardening.gen.GeneratedOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.GraphLimits;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.AssignmentForm;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.AssignmentRequirement;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.Request;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.graph.TransitionGraph;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reusable decoder of graph inputs (ADR 0023 §2, §3): a control graph whose nodes carry
 * assignment-only actions, tracked by the variable {@code pc}, and the invariant {@code pc /= k}.
 *
 * <p>The graph has a spine {@code 0 -> 1 -> ... -> N-1}, so node u is at most u steps from node 0,
 * and drawn extra edges add back edges, self-loops and short forward skips. The payload begins
 * with a header, the node count N, the target k and the step bound B, and the rest is divided
 * among the {@link GraphSection}s. The edge and node sections are each split again into N equal
 * slices: slice u of the first holds the extra successors of node u, and slice u of the second
 * its action. Like {@link IrSpecGeneratorEngine}, the engine stores only immutable
 * configuration, and every invocation creates its own context.
 *
 * <p>Every edge leaving the current node is enabled while the step counter is below its bound: the
 * initial predicate and every node action assign exact values, and an edge has no guard but
 * {@code pc = u}, the step guard and tautologies. That is what makes the outcome of the invariant
 * predictable from the graph alone ({@link GeneratedGraph#targetDistance()}).
 *
 * <p>With {@link EdgeGuards}, the {@link GraphSection#GUARDS} section is one cursor over the edges
 * in edge order: for each edge, a terminated list of at most {@link GraphLimits#maximumGuards()}
 * guards, which the edge conjoins after {@code pc = u} (ADR 0023 §6). Each guard reads at most
 * {@link #GUARD_BYTES} bytes of it. The section is not sliced: a short section, split into N
 * slices, would give its bytes to the last node, while read in edge order it guards the edges
 * leaving node 0 first, where every path to the target starts. Without guards the section is
 * empty, so the other sections do not move.
 */
@ArchRole(Role.FACADE)
@Key
public final class IrGraphGeneratorEngine {
    private static final String NODE_PREFIX = "Node";
    /**
     * The bytes of the guard section one guard may read, so that a guard's operands cannot drain
     * the section and leave the later edges unguarded. Part of the byte encoding.
     */
    static final int GUARD_BYTES = 8;

    private final IrGenerationConfig config;
    private final Optional<EdgeGuards> guards;

    /**
     * Creates a reusable engine with the supplied generation settings, whose edges have no
     * tautological guards.
     *
     * @throws IllegalArgumentException if the configured weights need more selection slots than
     *     one index can address
     */
    public IrGraphGeneratorEngine(IrGenerationConfig config) {
        this(config, Optional.empty());
    }

    /**
     * Creates a reusable engine whose edges conjoin tautological guards drawn by {@code guards}.
     *
     * @throws IllegalArgumentException if the configured weights need more selection slots than
     *     one index can address
     */
    public IrGraphGeneratorEngine(IrGenerationConfig config, EdgeGuards guards) {
        this(config, Optional.of(Objects.requireNonNull(guards, "guards")));
    }

    private IrGraphGeneratorEngine(IrGenerationConfig config, Optional<EdgeGuards> guards) {
        this.config = Objects.requireNonNull(config, "config");
        this.guards = guards;
        ExpressionKindCatalog.requireAddressableSlots(this.config);
        CustomExpressionKind.requireUsableLibrary(this.config);
    }

    /**
     * Generates one graph module from the remaining bytes of {@code draw}, consuming all of them.
     *
     * @throws RuntimeException if the builder rejects an internally constructed expression
     */
    public GeneratedGraph generate(Draw draw) {
        Objects.requireNonNull(draw, "draw");
        var limits = this.config.graphs();
        var header = Header.draw(draw, limits);
        var nodeCount = header.nodes();
        // Every disjunct is guarded by the entry's own bound, which ActionGenFactory reads.
        var config = this.config.withModuleLimits(this.config.modules().withMaximumSteps(header.bound()));
        var sections = GraphSection.split(draw, guards.isPresent());
        var context = new GenerationContext(config);
        var types = new IrTypeGenFactory(context);
        var expressions = new IrExprGenFactory(context, types);
        var shapes = new ActionShapeGenFactory(context, types, expressions);
        var depth = config.expressions().maximumExpressionDepth();

        var pc = ScopedName.stateVariable(GeneratedGraph.PC_VARIABLE, PrimitiveType.INT);
        var declared = sections.get(GraphSection.VARIABLES).draw(
                ModulePrelude.variables(context, types, List.of(pc)));
        var data = declared.data();
        var actions = new ActionGenFactory(context, types, expressions, data, declared.step());
        var auxiliaries = sections.get(GraphSection.AUXILIARY_OPERATORS).draw(
                ModulePrelude.auxiliaryOperators(context, types, expressions));
        var operatorNames = ModulePrelude.bindings(auxiliaries);
        // Right-hand sides read neither pc nor step: a variable assigned from either would change
        // with control alone (ir-generators.md §9.1).
        var actionScope = new ArrayList<ScopedName>(operatorNames);
        actionScope.addAll(data);

        var init = sections.get(GraphSection.INIT).draw(context.withBindings(operatorNames,
                context.withFreshNodeBudget(actions.initPredicate(depth, AssignmentForm.EXACT, List.of(pc)))));

        var operators = new ArrayList<GeneratedOperator>();
        auxiliaries.forEach(operator -> operators.add(operator.generated()));
        var effect = new ActionEffect(data.stream().map(ScopedName::name).toList());
        var successors = new ArrayList<List<Integer>>();
        var edgeSlices = WeightedSlices.equal(sections.get(GraphSection.EDGES), nodeCount);
        var nodeSlices = WeightedSlices.equal(sections.get(GraphSection.NODES), nodeCount);
        for (var node = 0; node < nodeCount; node++) {
            successors.add(edgeSlices.get(node).draw(successors(node, nodeCount, limits.maximumExtraSuccessors())));
            var body = nodeSlices.get(node).draw(context.withBindings(actionScope, context.withDefinitionBoundary(
                    context.withFreshNodeBudget(shapes.exactLeaf(
                            new Request(data, 0, depth, AssignmentRequirement.REQUIRED)).map(shapes::conjoin)))));
            operators.add(new GeneratedActionOperator(context.builder().decl(nodeName(node), body), effect));
        }
        var graph = new TransitionGraph(successors);
        var edgeGuards = guards.isEmpty() ? noGuards(graph) : drawGuards(config, sections.get(GraphSection.GUARDS),
                graph, namesInUse(declared.declarations(), operators, init), actionScope);

        var next = nextAction(actions, shapes, pc, graph, edgeGuards);
        var invariant = context.builder().neql(shapes.nameOf(pc), context.builder().integer(header.target()));
        var spec = new GeneratedSpec(declared.declarations(), operators, init, next, invariant,
                Optional.empty(), header.bound());
        return new GeneratedGraph(spec, graph, header.target());
    }

    /**
     * The first bytes of a graph input: the node count N, the target k in the upper half of the
     * spine, and the step bound B, at least half the spine and at most its length, capped by
     * {@link GraphLimits#maximumSteps()}. A target past the bound
     * is unreachable, so about half the inputs predict a pass.
     */
    private record Header(int nodes, int target, int bound) {
        static Header draw(Draw draw, GraphLimits limits) {
            var nodes = Math.toIntExact(draw.drawLong(limits.minimumNodes(), limits.maximumNodes()));
            var target = nodes == 1 ? 0 : Math.toIntExact(draw.drawLong((nodes + 1) / 2, nodes - 1L));
            var cap = limits.maximumSteps();
            var bound = Math.toIntExact(draw.drawLong(Math.min(nodes / 2, cap), Math.min(nodes - 1, cap)));
            return new Header(nodes, target, bound);
        }
    }

    /** No guard on any edge. */
    private static List<List<TlaEx>> noGuards(TransitionGraph graph) {
        return graph.edges().stream().map(edge -> List.<TlaEx>of()).toList();
    }

    /** Returns every name a module of these declarations and bodies uses. */
    private static Set<String> namesInUse(List<? extends TlaDecl> variables, List<GeneratedOperator> operators,
            TlaEx init) {
        var names = new LinkedHashSet<String>();
        variables.forEach(variable -> names.add(variable.name()));
        for (var operator : operators) {
            names.add(operator.declaration().name());
            names.addAll(IrNames.used(operator.declaration().body()));
        }
        names.addAll(IrNames.used(init));
        return names;
    }

    /**
     * Draws the guards of every edge, in edge order, from the one cursor of the guard section: a
     * terminated list per edge, each guard from at most {@link #GUARD_BYTES} bytes, so an exhausted
     * section leaves the later edges unguarded. The operands avoid every name in use, and see what
     * a node action sees: the auxiliary operators and the data variables.
     */
    private List<List<TlaEx>> drawGuards(IrGenerationConfig config, Draw section, TransitionGraph graph,
            Set<String> namesInUse, List<ScopedName> scope) {
        var operands = new OperandGenerator(config, namesInUse);
        var guard = guards.orElseThrow().guard(operands, scope.stream().map(OperandGenerator.Name::of).toList());
        if (guard.isEmpty()) {
            return noGuards(graph);
        }
        Generator<TlaEx> bounded = draw -> draw.slice(Math.min(GUARD_BYTES, draw.remaining())).draw(guard.get());
        var list = BasicGenerators.listOf(bounded, 0, config.graphs().maximumGuards());
        var result = new ArrayList<List<TlaEx>>();
        for (var ignored : graph.edges()) {
            result.add(section.draw(list));
        }
        return result;
    }

    /**
     * One bounded step per edge (u, v), in edge order: {@code pc = u /\ g1 /\ ... /\ gk /\ Node<u> /\ pc' = v},
     * where the g are the edge's guards.
     */
    private static TlaEx nextAction(ActionGenFactory actions, ActionShapeGenFactory shapes, ScopedName pc,
                                    TransitionGraph graph, List<List<TlaEx>> guards) {
        var builder = shapes.builder();
        var nodeType = new OperatorType(List.of(), PrimitiveType.BOOL).toTlaType();
        var edges = new ArrayList<TlaEx>();
        var allEdges = graph.edges();
        for (var index = 0; index < allEdges.size(); index++) {
            var edge = allEdges.get(index);
            var conjuncts = new ArrayList<TlaEx>();
            conjuncts.add(builder.eql(shapes.nameOf(pc), builder.integer(edge.source())));
            conjuncts.addAll(guards.get(index));
            conjuncts.add(builder.operApply(builder.name(nodeName(edge.source()), nodeType)));
            conjuncts.add(builder.primeEq(shapes.nameOf(pc), builder.integer(edge.target())));
            edges.add(actions.boundedStep(conjuncts, List.of()));
        }
        return builder.or(BuilderArrays.expressions(edges));
    }

    /**
     * The successors of {@code node}: its spine successor {@code node + 1}, byte-free, then a
     * terminated list of extra successors, each a back edge, a self-loop or a forward skip of at
     * most two nodes, with repeats removed in order. The last node has no spine successor, except
     * in a one-node graph, where the spine is the self-loop {@code 0 -> 0}: Next then always has a
     * disjunct, since Apalache rejects {@code FALSE \/ UNCHANGED vars}.
     */
    private static Generator<List<Integer>> successors(int node, int nodeCount, int maximumExtra) {
        var spine = nodeCount == 1 ? List.of(0) : node + 1 < nodeCount ? List.of(node + 1) : List.<Integer>of();
        var farthest = Math.min(node + 2, nodeCount - 1L);
        return BasicGenerators.listOf((Draw draw) -> Math.toIntExact(draw.drawLong(0, farthest)), 0, maximumExtra)
                .map(extra -> {
                    var all = new LinkedHashSet<Integer>(spine);
                    all.addAll(extra);
                    return List.copyOf(all);
                });
    }

    private static String nodeName(int node) {
        return NODE_PREFIX + node;
    }
}
