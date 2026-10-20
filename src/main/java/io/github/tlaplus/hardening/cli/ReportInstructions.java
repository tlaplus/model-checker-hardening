package io.github.tlaplus.hardening.cli;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The text of {@code fuzztla report}. The campaign report is written by {@code script/report.py},
 * which plots with Matplotlib, so the command only tells the user how to run the script.
 */
final class ReportInstructions {
    static final Path SCRIPT = Path.of("script", "report.py");
    static final Path REQUIREMENTS = Path.of("script", "requirements.txt");
    static final String MANUAL = "docs/manual/campaign-report.md";

    private ReportInstructions() {}

    /** Renders the instructions for a corpus, with paths in {@code project} when it is known. */
    static String render(Optional<Path> project, Path corpus) {
        var script = project.map(directory -> directory.resolve(SCRIPT)).orElse(SCRIPT);
        var requirements = project.map(directory -> directory.resolve(REQUIREMENTS)).orElse(REQUIREMENTS);
        return String.join(
                System.lineSeparator(),
                "The campaign report is written by a Python script. Run:",
                "  python3 " + script + " " + corpus,
                "It writes " + corpus.resolve("report.md") + " and its images to " + corpus.resolve(".report") + ".",
                "Install the script's dependencies once with:",
                "  python3 -m pip install -r " + requirements,
                "See " + MANUAL + ".",
                "");
    }
}
