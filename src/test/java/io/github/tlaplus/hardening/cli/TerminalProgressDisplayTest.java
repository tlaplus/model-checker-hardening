package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import io.github.tlaplus.hardening.workflow.parser.ParserStageSummary;
import io.github.tlaplus.hardening.workflow.tlc.TlcStageSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerminalProgressDisplayTest {
    @Test
    void replacesLiveTablesAndLeavesOnlyTheFinalTableVisible() {
        var output = new StringWriter();
        var display = new TerminalProgressDisplay(new PrintWriter(output));
        var first = RunTable.progress(progress(WorkflowProgress.Phase.RUNNING, 1, 0));
        var second = RunTable.progress(progress(WorkflowProgress.Phase.RUNNING, 3, 2));
        var finished = "finished" + System.lineSeparator();

        display.update(progress(WorkflowProgress.Phase.RUNNING, 1, 0));
        display.update(progress(WorkflowProgress.Phase.RUNNING, 3, 2));
        display.finish(finished);
        display.close();

        assertEquals(
                first
                        + erase(first)
                        + second
                        + erase(second)
                        + finished,
                output.toString());
    }

    @Test
    void clearsAnUnfinishedTableBeforeAnErrorIsPrinted() {
        var output = new StringWriter();
        var display = new TerminalProgressDisplay(new PrintWriter(output));
        var table = RunTable.progress(progress(WorkflowProgress.Phase.RUNNING, 2, 1));

        display.update(progress(WorkflowProgress.Phase.RUNNING, 2, 1));
        display.close();
        display.close();

        assertEquals(table + erase(table), output.toString());
    }

    @Test
    void rendersTheFinalizingPhase() {
        var table = RunTable.progress(
                progress(WorkflowProgress.Phase.FINALIZING, 3, 3));

        assertTrue(table.lines().anyMatch(line -> line.contains("FINALIZING")
                && line.contains("run state")));
    }

    @Test
    void rendersAdmittedInputRichnessStatistics() {
        var snapshot = new WorkflowProgress(
                WorkflowProgress.Phase.RUNNING,
                new PbtStageSummary(42, 0, 3, 5, 0, 1, 1, 1.25, 9.0, 4.5),
                new ParserStageSummary(2, 0, 0),
                new TlcStageSummary(1, 0, 0),
                3,
                1,
                1,
                2);

        var table = RunTable.progress(snapshot);

        assertTrue(table.lines()
                .anyMatch(line -> line.contains("1.25") && line.contains("min richness")));
        assertTrue(table.lines()
                .anyMatch(line -> line.contains("9") && line.contains("max richness")));
        assertTrue(table.lines()
                .anyMatch(line -> line.contains("4.5") && line.contains("avg richness")));

        var emptyTable = RunTable.progress(progress(WorkflowProgress.Phase.RUNNING, 0, 0));
        assertEquals(
                3,
                emptyTable.lines()
                        .filter(line -> line.contains("n/a") && line.contains("richness"))
                        .count());
    }

    @Test
    void omitsTheSeedFromLiveAndFinalTables() {
        var snapshot = progress(WorkflowProgress.Phase.RUNNING, 3, 1);
        var summary = new WorkflowRunSummary(
                WorkflowRunSummary.StopReason.COMPLETED,
                snapshot.generator(),
                snapshot.parser(),
                snapshot.tlc(),
                new CorpusInventory(
                        List.of(), List.of(), 1, 1, 0, 1, 0, 0, 1));

        assertFalse(RunTable.progress(snapshot).contains("random seed"));
        assertFalse(RunTable.finished(Path.of("corpus"), summary).contains("random seed"));
    }

    private WorkflowProgress progress(
            WorkflowProgress.Phase phase, long generated, long parsed) {
        return new WorkflowProgress(
                phase,
                new PbtStageSummary(
                        42, 0, generated, generated, 0, 0, 0, 0.0, 0.0, 0.0),
                new ParserStageSummary(parsed, 0, 0),
                new TlcStageSummary(parsed, 0, 0),
                generated,
                generated - parsed,
                0,
                parsed);
    }

    private String erase(String table) {
        return "\u001b[" + table.lines().count() + "F\u001b[J";
    }
}
