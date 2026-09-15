package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.database.CorpusExport;
import java.util.Objects;

/** Renders the outcome of a corpus export. */
final class ExportDbReport {
    private ExportDbReport() {}

    static String render(CorpusExport.Summary summary) {
        Objects.requireNonNull(summary, "summary");
        return String.format(
                "exported %d entries (%d unreadable, %d vanished) to %s%n",
                summary.entries(),
                summary.unreadable(),
                summary.vanished(),
                summary.output());
    }
}
