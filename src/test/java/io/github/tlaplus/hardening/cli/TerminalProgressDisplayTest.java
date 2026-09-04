package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageEntryCounts;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
                new GeneratorSummary(
                        42, 3, 5, 0, 1, 1, 3, 1.25, 9.0, 4.5, Duration.ofSeconds(3)),
                Map.of(
                        CorpusStage.PARSER,
                        summary(2, Duration.ofSeconds(2)),
                        CorpusStage.TLC,
                        summary(1, Duration.ofSeconds(1)),
                        CorpusStage.APALACHE,
                        StageVerdictSummary.empty(),
                        CorpusStage.AGGREGATOR,
                        StageVerdictSummary.empty()),
                Map.of(
                        CorpusStage.PARSER, 1L,
                        CorpusStage.TLC, 1L,
                        CorpusStage.APALACHE, 2L,
                        CorpusStage.AGGREGATOR, 0L),
                3,
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
    void rendersCounterexamplesSeparatelyFromFailures() {
        var base = progress(WorkflowProgress.Phase.RUNNING, 2, 0);
        var stages = new EnumMap<CorpusStage, StageVerdictSummary>(base.stages());
        stages.put(
                CorpusStage.TLC,
                new StageVerdictSummary(
                        new StageEntryCounts(Map.of(
                                CorpusVerdict.COUNTEREXAMPLE, 2L,
                                CorpusVerdict.FAIL, 1L)),
                        Duration.ZERO));
        var snapshot = new WorkflowProgress(
                base.phase(),
                base.generator(),
                stages,
                base.backlog(),
                base.corpusEntries(),
                base.totalElapsed());

        var table = RunTable.progress(snapshot);

        assertTrue(table.lines().anyMatch(line -> line.contains("2")
                && line.contains("TLC counterexamples")), table);
        assertTrue(table.lines().anyMatch(line -> line.contains("1")
                && line.contains("TLC failed")), table);
        assertFalse(table.contains("parser counterexamples"), table);
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
        var stages = new EnumMap<CorpusStage, CorpusInventory.StageEntries>(CorpusStage.class);
        stages.put(
                CorpusStage.PARSER,
                new CorpusInventory.StageEntries(
                        List.of(),
                        new StageEntryCounts(Map.of(
                                CorpusVerdict.PASS, 1L,
                                CorpusVerdict.FAIL, 1L)),
                        1));
        for (var checker : CorpusStage.checkerBranches()) {
            stages.put(
                    checker,
                    new CorpusInventory.StageEntries(
                            List.of(),
                            new StageEntryCounts(Map.of(CorpusVerdict.PASS, 1L)),
                            1));
        }
        stages.put(
                CorpusStage.AGGREGATOR,
                new CorpusInventory.StageEntries(List.of(), StageEntryCounts.empty(), 0));
        var summary = new WorkflowRunSummary(
                WorkflowRunSummary.StopReason.COMPLETED,
                snapshot.generator(),
                snapshot.stages(),
                new CorpusInventory(stages),
                snapshot.totalElapsed());

        assertFalse(RunTable.progress(snapshot).contains("random seed"));
        assertFalse(RunTable.finished(Path.of("corpus"), summary).contains("random seed"));
    }

    private WorkflowProgress progress(
            WorkflowProgress.Phase phase, long generated, long parsed) {
        return new WorkflowProgress(
                phase,
                new GeneratorSummary(
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
                stageSummaries(summary(parsed, Duration.ofSeconds(parsed))),
                Map.of(
                        CorpusStage.PARSER, generated - parsed,
                        CorpusStage.TLC, 0L,
                        CorpusStage.APALACHE, 0L,
                        CorpusStage.AGGREGATOR, 0L),
                generated,
                Duration.ofSeconds(generated));
    }

    private static StageVerdictSummary summary(long passed, Duration elapsed) {
        return new StageVerdictSummary(
                new StageEntryCounts(Map.of(CorpusVerdict.PASS, passed)), elapsed);
    }

    private Map<CorpusStage, StageVerdictSummary> stageSummaries(StageVerdictSummary summary) {
        var stages = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            stages.put(stage, summary);
        }
        return stages;
    }

    private String erase(String table) {
        return "\u001b[" + table.lines().count() + "F\u001b[J";
    }
}
