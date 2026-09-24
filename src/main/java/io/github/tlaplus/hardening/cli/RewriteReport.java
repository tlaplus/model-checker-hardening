package io.github.tlaplus.hardening.cli;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Renders a metamorphic pair for triage (ADR 0016 §8): which side the checkers explore, the rules
 * applied in order, and both sides. An expression side is rendered as an expression, a module side
 * as the conformance module it would be on its own.
 */
final class RewriteReport {
    private RewriteReport() {}

    static String render(Rewrite<?> rewrite) {
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
}
