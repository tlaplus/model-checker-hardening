package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.GraphOutcomes;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Renders a decoded input independently of picocli command wiring. By default, a metamorphic pair
 * and a graph module are rendered for triage; any other input as its module or expression.
 */
final class DecodedInputRenderer {
    enum Mode {
        DEFAULT,
        SPECIFICATION,
        APALACHE_IR
    }

    private DecodedInputRenderer() {}

    static String render(SpecArtifact artifact, Mode mode) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case DEFAULT -> artifact.rewrite().map(DecodedInputRenderer::rewrite)
                    .or(() -> artifact.graph().map(graph -> graph(graph, SpecText.render(artifact))))
                    .orElseGet(() -> artifact.standaloneExpression()
                            .map(EnvelopeReport::expression)
                            .orElseGet(() -> SpecText.render(artifact)));
            case SPECIFICATION -> SpecText.render(artifact);
            case APALACHE_IR -> ApalacheIrJson.render(artifact.module());
        };
    }

    /**
     * Renders a metamorphic pair (ADR 0016 §8): which side the checkers explore, the rules applied
     * in order, and both sides. An expression side is rendered as an expression, a module side as
     * the conformance module it would be on its own.
     */
    static String rewrite(Rewrite<?> rewrite) {
        Objects.requireNonNull(rewrite, "rewrite");
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("explored: %s%n",
                    rewrite.orientation() == Orientation.EXPLORE_ORIGINAL ? "original" : "rewrite");
            writer.printf("rules: %s%n", String.join(", ", rewrite.appliedRules()));
            writer.printf("original:%n%s%n", side(rewrite.original()).stripTrailing());
            writer.printf("rewrite:%n%s%n", side(rewrite.rewritten()).stripTrailing());
        }
        return output.toString();
    }

    private static String side(Object side) {
        return switch (side) {
            case TlaEx expression -> EnvelopeReport.expression(expression);
            case GeneratedSpec spec -> SpecText.render(FuzzInputModule.create(spec));
            default -> throw new IllegalArgumentException("unsupported side: " + side.getClass());
        };
    }

    /**
     * Renders a graph module (ADR 0023): the control graph, the target, the outcome the graph
     * predicts, and the module the checkers see.
     */
    private static String graph(GeneratedGraph graph, String module) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            var edges = graph.graph().edges().stream()
                    .map(edge -> edge.source() + " -> " + edge.target())
                    .collect(Collectors.joining(", "));
            writer.printf("nodes: %d%n", graph.graph().nodeCount());
            writer.printf("edges: %s%n", edges);
            var distance = graph.graph().distance(graph.target());
            writer.printf("target: %d (%s)%n", graph.target(), distance.isEmpty() ? "unreachable"
                    : "distance " + distance.getAsInt() + ", step bound " + graph.spec().stepBound());
            var expected = GraphOutcomes.of(graph);
            writer.printf("expected: %s%s%n", expected.verdict().encodedName(),
                    expected.traceLength().isPresent() ? ", trace length " + expected.traceLength().getAsInt() : "");
            writer.printf("module:%n%s%n", module.stripTrailing());
        }
        return output.toString();
    }
}
