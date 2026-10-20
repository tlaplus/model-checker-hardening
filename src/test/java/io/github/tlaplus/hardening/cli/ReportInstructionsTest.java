package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ReportInstructionsTest {
    @Test
    void namesTheScriptAndItsRequirementsInTheProject() {
        var project = Path.of("/opt/fuzztla");

        var text = ReportInstructions.render(Optional.of(project), Path.of("corpus66"));

        assertEquals(
                String.join(
                        System.lineSeparator(),
                        "The campaign report is written by a Python script. Run:",
                        "  python3 " + project.resolve("script/report.py") + " corpus66",
                        "It writes " + Path.of("corpus66", "report.md") + " and its images to "
                                + Path.of("corpus66", ".report") + ".",
                        "Install the script's dependencies once with:",
                        "  python3 -m pip install -r " + project.resolve("script/requirements.txt"),
                        "See docs/manual/campaign-report.md.",
                        ""),
                text);
    }

    @Test
    void fallsBackToPathsRelativeToTheProjectWithoutAHome() {
        var text = ReportInstructions.render(Optional.empty(), Path.of("corpus"));

        assertTrue(text.contains("  python3 " + Path.of("script", "report.py") + " corpus"), text);
    }

    @Test
    void reportCommandPrintsTheInstructionsAndSucceeds() {
        var out = new StringWriter();
        var command = new CommandLine(new FuzzTlaCommand()).setOut(new PrintWriter(out));

        var status = command.execute("report", "--corpus", "corpus7");

        assertEquals(CommandLine.ExitCode.OK, status);
        assertTrue(out.toString().contains(Path.of("script", "report.py") + " corpus7"), out.toString());
    }
}
