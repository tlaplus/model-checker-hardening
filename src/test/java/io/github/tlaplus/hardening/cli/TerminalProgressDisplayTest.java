package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
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
                new PbtStageSummary(
                        42, 3, 5, 0, 1, 1, 3, 1.25, 9.0, 4.5, Duration.ofSeconds(3)),
                new StageVerdictSummary(2, 0, 0, Duration.ofSeconds(2)),
                new StageVerdictSummary(1, 0, 0, Duration.ofSeconds(1)),
                new StageVerdictSummary(0, 0, 0, Duration.ZERO),
                3,
                1,
                1,
                2,
                Duration.ofSeconds(4));

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
    void rendersStageAndTotalElapsedTime() {
        var table = RunTable.progress(progress(WorkflowProgress.Phase.RUNNING, 65, 3));

        assertTrue(table.lines()
                .anyMatch(line -> line.contains("1m 5s") && line.contains("generator elapsed")));
        assertTrue(table.lines()
                .anyMatch(line -> line.contains("3s") && line.contains("parser elapsed")));
        assertTrue(table.lines()
                .anyMatch(line -> line.contains("1m 5s") && line.contains("total elapsed")));
    }

    @Test
    void omitsTheSeedFromLiveAndFinalTables() {
        var snapshot = progress(WorkflowProgress.Phase.RUNNING, 3, 1);
        var summary = new WorkflowRunSummary(
                WorkflowRunSummary.StopReason.COMPLETED,
                snapshot.generator(),
                snapshot.parser(),
                snapshot.tlc(),
                snapshot.apalache(),
                new CorpusInventory(
                        List.of(),
                        List.of(),
                        List.of(),
                        1,
                        1,
                        0,
                        1,
                        0,
                        0,
                        1,
                        0,
                        0),
                snapshot.totalElapsed());

        assertFalse(RunTable.progress(snapshot).contains("random seed"));
        assertFalse(RunTable.finished(Path.of("corpus"), summary).contains("random seed"));
    }

    private WorkflowProgress progress(
            WorkflowProgress.Phase phase, long generated, long parsed) {
        return new WorkflowProgress(
                phase,
                new PbtStageSummary(
                        42,
                        generated,
                        generated,
                        0,
                        0,
                        0,
                        0,
                        0.0,
                        0.0,
                        0.0,
                        Duration.ofSeconds(generated)),
                new StageVerdictSummary(parsed, 0, 0, Duration.ofSeconds(parsed)),
                new StageVerdictSummary(parsed, 0, 0, Duration.ofSeconds(parsed)),
                new StageVerdictSummary(parsed, 0, 0, Duration.ofSeconds(parsed)),
                generated,
                generated - parsed,
                0,
                0,
                Duration.ofSeconds(generated));
    }

    private String erase(String table) {
        return "\u001b[" + table.lines().count() + "F\u001b[J";
    }
}
