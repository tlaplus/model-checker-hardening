package io.github.tlaplus.hardening.gen.engine;

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
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.AssignmentForm;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.AssignmentRequirement;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.Request;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.graph.TransitionGraph;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reusable decoder of graph inputs (ADR 0023 §2, §3): a control graph whose nodes carry
 * assignment-only actions, tracked by the variable {@code pc}, and the invariant {@code pc /= k}.
 *
 * <p>The payload begins with a header, the node count N and the target k, which is not the initial
 * node unless N = 1, and the rest is divided
 * among the {@link GraphSection}s. The edge and node sections are each split again into N equal
 * slices: slice u of the first holds the successors of node u, and slice u of the second its
 * action. Like {@link IrSpecGeneratorEngine}, the engine
 * stores only immutable configuration, and every invocation creates its own context.
 *
 * <p>Every edge leaving the current node is enabled while the step counter is below its bound: the
 * initial predicate and every node action assign exact values, and an edge has no guard but
 * {@code pc = u} and the step guard. That is what makes the outcome of the invariant predictable
 * from the graph alone ({@link GeneratedGraph#targetDistance()}).
 */
@ArchRole(Role.FACADE)
@Key
public final class IrGraphGeneratorEngine {
    private static final String NODE_PREFIX = "Node";

    private final IrGenerationConfig config;

    /**
     * Creates a reusable engine with the supplied generation settings.
     *
     * @throws IllegalArgumentException if the configured weights need more selection slots than
     *     one index can address
     */
    public IrGraphGeneratorEngine(IrGenerationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
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
        var limits = config.graphs();
        var nodeCount = Math.toIntExact(draw.drawLong(1, limits.maximumNodes()));
        // The initial node is a target only when it is the only node: `pc /= 0` fails in every
        // initial state, so such a module explores nothing.
        var target = nodeCount == 1 ? 0 : Math.toIntExact(draw.drawLong(1, nodeCount - 1L));
        var sections = GraphSection.split(draw);
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
        var nodeType = new OperatorType(List.of(), PrimitiveType.BOOL);
        var successors = new ArrayList<List<Integer>>();
        var edgeSlices = WeightedSlices.equal(sections.get(GraphSection.EDGES), nodeCount);
        var nodeSlices = WeightedSlices.equal(sections.get(GraphSection.NODES), nodeCount);
        for (var node = 0; node < nodeCount; node++) {
            // The initial node always has a successor, so Next has a disjunct that assigns every
            // variable: Apalache rejects a `FALSE` disjunct, which assigns none.
            successors.add(edgeSlices.get(node).draw(
                    successorList(nodeCount, node == 0 ? 1 : 0, limits.maximumSuccessors())));
            var body = nodeSlices.get(node).draw(context.withBindings(actionScope, context.withDefinitionBoundary(
                    context.withFreshNodeBudget(shapes.exactLeaf(
                            new Request(data, 0, depth, AssignmentRequirement.REQUIRED)).map(shapes::conjoin)))));
            operators.add(new GeneratedActionOperator(context.builder().decl(nodeName(node), body), effect));
        }
        var graph = new TransitionGraph(successors);

        var builder = context.builder();
        var edges = new ArrayList<TlaEx>();
        for (var edge : graph.edges()) {
            edges.add(actions.boundedStep(List.of(
                    builder.eql(shapes.nameOf(pc), builder.integer(edge.source())),
                    builder.operApply(builder.name(nodeName(edge.source()), nodeType.toTlaType())),
                    builder.primeEq(shapes.nameOf(pc), builder.integer(edge.target()))), List.of()));
        }
        var next = builder.or(BuilderArrays.expressions(edges));
        var invariant = builder.neql(shapes.nameOf(pc), builder.integer(target));
        var spec = new GeneratedSpec(declared.declarations(), operators, init, next, invariant,
                Optional.empty(), config.modules().maximumSteps());
        return new GeneratedGraph(spec, graph, target);
    }

    /** A terminated list of node indices, with repeats removed in order. */
    private static Generator<List<Integer>> successorList(int nodeCount, int minimum, int maximum) {
        return BasicGenerators.listOf(
                        (Draw draw) -> Math.toIntExact(draw.drawLong(0, nodeCount - 1L)), minimum, maximum)
                .map(drawn -> List.copyOf(new LinkedHashSet<>(drawn)));
    }

    private static String nodeName(int node) {
        return NODE_PREFIX + node;
    }
}
