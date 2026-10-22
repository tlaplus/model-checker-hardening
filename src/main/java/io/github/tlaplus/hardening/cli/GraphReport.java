package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.workflow.spec.GraphOutcomes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Renders a graph module for triage (ADR 0023): the control graph, the target, the outcome the
 * graph predicts, and the module the checkers see.
 */
final class GraphReport {
    private GraphReport() {}

    static String render(GeneratedGraph graph, String module) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(module, "module");
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
