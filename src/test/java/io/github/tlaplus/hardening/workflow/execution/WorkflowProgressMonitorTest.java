package io.github.tlaplus.hardening.workflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkflowProgressMonitorTest {
    @Test
    void publishesInitialPeriodicAndFinalSnapshotsWithoutConcurrentCallbacks()
            throws Exception {
        var sequence = new AtomicInteger();
        var activeCallbacks = new AtomicInteger();
        var maximumActiveCallbacks = new AtomicInteger();
        var firstTwoUpdates = new CountDownLatch(2);
        var observed = new CopyOnWriteArrayList<WorkflowProgress>();

        var monitor = WorkflowProgressMonitor.start(
                Duration.ofMillis(10),
                () -> progress(sequence.getAndIncrement()),
                snapshot -> {
                    var active = activeCallbacks.incrementAndGet();
                    maximumActiveCallbacks.accumulateAndGet(active, Math::max);
                    observed.add(snapshot);
                    firstTwoUpdates.countDown();
                    activeCallbacks.decrementAndGet();
                });

        assertTrue(firstTwoUpdates.await(1, TimeUnit.SECONDS));
        monitor.close();

        assertTrue(observed.size() >= 3);
        assertEquals(1, maximumActiveCallbacks.get());
        for (var index = 1; index < observed.size(); index++) {
            assertTrue(observed.get(index).corpusEntries()
                    > observed.get(index - 1).corpusEntries());
        }
    }

    @Test
    void disablesAListenerThatThrows() {
        var calls = new AtomicInteger();

        try (var ignored = WorkflowProgressMonitor.start(
                Duration.ofMillis(1),
                () -> progress(0),
                _ -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("display failed");
                })) {
            // The initial callback disables reporting before a worker is started.
        }

        assertEquals(1, calls.get());
    }

    private static Map<CorpusStage, StageVerdictSummary> stageSummaries() {
        var stages = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            stages.put(stage, new StageVerdictSummary(0, 0, 0, Duration.ZERO));
        }
        return stages;
    }

    private static Map<CorpusStage, Long> backlog() {
        var backlog = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            backlog.put(stage, 0L);
        }
        return backlog;
    }

    private WorkflowProgress progress(int value) {
        return new WorkflowProgress(
                WorkflowProgress.Phase.RUNNING,
                new GeneratorSummary(
                        1,
                        value,
                        value,
                        0,
                        0,
                        0,
                        0,
                        0.0,
                        0.0,
                        0.0,
                        Duration.ofSeconds(value)),
                stageSummaries(),
                backlog(),
                value,
                Duration.ofSeconds(value));
    }
}
