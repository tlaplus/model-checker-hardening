package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CheckerSet;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.EntryName;
import io.github.tlaplus.hardening.corpus.EntryProgress;
import io.github.tlaplus.hardening.corpus.StageEntryCounts;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowEvents;
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
        var progress = new GenerationProgress(emptyInventory(), control, CheckerSet.ALL);
        var entry = new EntryName("entry.cbor");
        progress.admitted(entry, 1, false);
        progress.completed(entry, CorpusStage.PARSER, CorpusVerdict.PASS);
        progress.completed(entry, CorpusStage.TLC, CorpusVerdict.CRASH);
        assertEquals(1, progress.unsettled(1));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var settled = executor.submit(() -> progress.awaitSettled(1));
            assertFalse(settled.isDone());
            progress.completed(entry, CorpusStage.APALACHE, CorpusVerdict.PASS);
            assertTrue(settled.get(1, TimeUnit.SECONDS));
        }
        assertEquals(0, progress.unsettled(1));
    }

    @Test
    void aggregationAndParserFailureAreTerminalAndStopWakesWaiters() throws Exception {
        var control = new WorkflowControl(new WorkQueue<>());
        var progress = new GenerationProgress(emptyInventory(), control, CheckerSet.ALL);
        var failed = new EntryName("failed.cbor");
        progress.admitted(failed, 0, false);
        progress.completed(failed, CorpusStage.PARSER, CorpusVerdict.FAIL);
        assertEquals(0, progress.unsettled(0));

        var aggregated = new EntryName("aggregated.cbor");
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

    /**
     * A checker makes its result visible before it reports it, and every checker hands the entry to
     * the aggregator. The faster checker's hand-off can therefore let the aggregator find both
     * results and report before the slower checker's own report arrives.
     */
    @Test
    void theAggregatorMayOvertakeTheSlowerCheckersReport() throws Exception {
        var control = new WorkflowControl(new WorkQueue<>());
        var progress = new GenerationProgress(emptyInventory(), control, CheckerSet.ALL);
        var entry = new EntryName("entry.cbor");
        progress.admitted(entry, 0, false);
        progress.completed(entry, CorpusStage.PARSER, CorpusVerdict.PASS);
        progress.completed(entry, CorpusStage.APALACHE, CorpusVerdict.PASS);

        progress.completed(entry, CorpusStage.AGGREGATOR, CorpusVerdict.PASS);
        assertEquals(0, progress.unsettled(0));
        assertTrue(progress.awaitSettled(0));

        progress.completed(entry, CorpusStage.TLC, CorpusVerdict.PASS);
        assertEquals(0, progress.unsettled(0));
        assertFalse(control.hasFailed());
    }

    @Test
    void aLateCheckerReportIsAcceptedOnceAndStrayReportsStillFail() {
        var control = new WorkflowControl(new WorkQueue<>());
        var progress = new GenerationProgress(emptyInventory(), control, CheckerSet.ALL);
        var entry = new EntryName("entry.cbor");
        progress.admitted(entry, 0, false);
        progress.completed(entry, CorpusStage.PARSER, CorpusVerdict.PASS);
        progress.completed(entry, CorpusStage.TLC, CorpusVerdict.PASS);
        progress.completed(entry, CorpusStage.AGGREGATOR, CorpusVerdict.PASS);

        // A checker that already reported cannot report again.
        assertThrows(IllegalStateException.class,
                () -> progress.completed(entry, CorpusStage.TLC, CorpusVerdict.PASS));
        // Nor can a stage that is not a late checker.
        assertThrows(IllegalStateException.class,
                () -> progress.completed(entry, CorpusStage.PARSER, CorpusVerdict.PASS));
        progress.completed(entry, CorpusStage.APALACHE, CorpusVerdict.PASS);
        // Once every checker has reported, the entry is forgotten entirely.
        assertThrows(IllegalStateException.class,
                () -> progress.completed(entry, CorpusStage.APALACHE, CorpusVerdict.PASS));
        assertThrows(IllegalStateException.class,
                () -> progress.completed(new EntryName("never.cbor"), CorpusStage.TLC, CorpusVerdict.PASS));
    }

    @Test
    void anUntrackedEntryOrdersLastInsteadOfFailingTheQueue() {
        var control = new WorkflowControl(new WorkQueue<>());
        var progress = new GenerationProgress(emptyInventory(), control, CheckerSet.ALL);
        var entry = new EntryName("entry.cbor");
        progress.admitted(entry, 3, false);

        assertEquals(3, progress.generationOf(entry));
        assertEquals(WorkflowEvents.UNKNOWN_GENERATION, progress.generationOf(new EntryName("gone.cbor")));

        // A settled entry is no longer tracked, and must still compare rather than throw.
        progress.completed(entry, CorpusStage.PARSER, CorpusVerdict.FAIL);
        assertEquals(WorkflowEvents.UNKNOWN_GENERATION, progress.generationOf(entry));
    }

    @Test
    void recoveredEntriesAndCountsSeedTheGenerationsTheRunResumes() {
        var stages = new EnumMap<CorpusStage, CorpusInventory.StageEntries>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            stages.put(stage, new CorpusInventory.StageEntries(List.of(), StageEntryCounts.empty(), 0));
        }
        var generations = new TreeMap<Integer, CorpusInventory.GenerationEntries>();
        generations.put(2, new CorpusInventory.GenerationEntries(6, 2, 1));
        var pending = new EntryName("pending.cbor");
        var inventory = new CorpusInventory(
                stages,
                generations,
                Map.of(pending, EntryProgress.admitted(2).with(CorpusStage.PARSER, CorpusVerdict.PASS)));
        var progress = new GenerationProgress(inventory, new WorkflowControl(new WorkQueue<>()), CheckerSet.ALL);

        assertEquals(6, progress.admitted(2));
        assertEquals(2, progress.mutants(2));
        assertEquals(1, progress.unsettled(2));
        assertEquals(2, progress.generationOf(pending));
    }

    private static CorpusInventory emptyInventory() {
        var stages = new EnumMap<CorpusStage, CorpusInventory.StageEntries>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            stages.put(stage, new CorpusInventory.StageEntries(List.of(), StageEntryCounts.empty(), 0));
        }
        return new CorpusInventory(stages, new TreeMap<>(), Map.of());
    }
}
