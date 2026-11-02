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
import io.github.tlaplus.hardening.workflow.tlc.TlcConfiguration;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
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
            case DEFAULT -> artifact.rewrite().map(rewrite -> rewrite(rewrite, artifact.request()))
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
     * the conformance module it would be on its own. When TLC checks the properties in runs of
     * their own, the configuration of each TLC run follows the rules (ADR 0027 §4).
     */
    static String rewrite(Rewrite<?> rewrite, CheckRequest request) {
        Objects.requireNonNull(rewrite, "rewrite");
        Objects.requireNonNull(request, "request");
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("explored: %s%n",
                    rewrite.orientation() == Orientation.EXPLORE_ORIGINAL ? "original" : "rewrite");
            writer.printf("rules: %s%n", String.join(", ", rewrite.appliedRules()));
            if (request.kind().hasPropertyRuns()) {
                writer.printf("tlc runs:%n");
                var runs = TlcConfiguration.texts(request);
                for (var index = 0; index < runs.size(); index++) {
                    writer.printf("  %d. %s%n", index + 1, String.join(", ", runs.get(index).strip().split("\n")));
                }
            }
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
