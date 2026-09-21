package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageEntryCounts;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GenerationProgressTest {
    @Test
    void aCrashSettlesOnlyAfterBothCheckerBranchesFinish() throws Exception {
        var control = new WorkflowControl(new WorkQueue<>());
        var progress = new GenerationProgress(emptyInventory(), control);
        var path = Path.of("entry.cbor");
        progress.admitted(path, 1, false);
        progress.completed(path, CorpusStage.PARSER, CorpusVerdict.PASS);
        progress.completed(path, CorpusStage.TLC, CorpusVerdict.CRASH);
        assertEquals(1, progress.unsettled(1));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var settled = executor.submit(() -> progress.awaitSettled(1));
            assertFalse(settled.isDone());
            progress.completed(path, CorpusStage.APALACHE, CorpusVerdict.PASS);
            assertTrue(settled.get(1, TimeUnit.SECONDS));
        }
        assertEquals(0, progress.unsettled(1));
    }

    @Test
    void aggregationAndParserFailureAreTerminalAndStopWakesWaiters() throws Exception {
        var control = new WorkflowControl(new WorkQueue<>());
        var progress = new GenerationProgress(emptyInventory(), control);
        var failed = Path.of("failed.cbor");
        progress.admitted(failed, 0, false);
        progress.completed(failed, CorpusStage.PARSER, CorpusVerdict.FAIL);
        assertEquals(0, progress.unsettled(0));

        var aggregated = Path.of("aggregated.cbor");
        progress.admitted(aggregated, 0, false);
        progress.completed(aggregated, CorpusStage.PARSER, CorpusVerdict.PASS);
        progress.completed(aggregated, CorpusStage.TLC, CorpusVerdict.PASS);
        progress.completed(aggregated, CorpusStage.APALACHE, CorpusVerdict.PASS);
        assertEquals(1, progress.unsettled(0));
        progress.completed(aggregated, CorpusStage.AGGREGATOR, CorpusVerdict.PASS);
        assertEquals(0, progress.unsettled(0));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var admission = executor.submit(() -> progress.awaitAdmitted(1, 1));
            control.capacityReached();
            assertFalse(admission.get(1, TimeUnit.SECONDS));
        }
    }

    private static CorpusInventory emptyInventory() {
        var stages = new EnumMap<CorpusStage, CorpusInventory.StageEntries>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            stages.put(stage, new CorpusInventory.StageEntries(List.of(), StageEntryCounts.empty(), 0));
        }
        return new CorpusInventory(stages, new TreeMap<>(), Map.of(), Map.of());
    }
}
