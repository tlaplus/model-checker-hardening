package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.signature.KnownDefectMatch;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;

/**
 * Renders the known-defect signatures that match one decoded input: each signature's id,
 * description, and references, and the subexpression it matched first, as TLA+.
 */
final class KnownDefectReport {
    static final String NO_MATCH = "no known defects";

    private KnownDefectReport() {}

    static String render(List<KnownDefectMatch> matches) {
        Objects.requireNonNull(matches, "matches");
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            if (matches.isEmpty()) {
                writer.printf("%s%n", NO_MATCH);
            }
            for (var match : matches) {
                var defect = match.defect();
                writer.printf("%s: %s%n", defect.id(), defect.description());
                defect.references().forEach(reference -> writer.printf("  reference: %s%n", reference));
                writer.printf("  matched:%n");
                EnvelopeReport.expression(match.witness())
                        .lines()
                        .forEach(line -> writer.printf("    %s%n", line));
            }
        }
        return output.toString();
    }
}
