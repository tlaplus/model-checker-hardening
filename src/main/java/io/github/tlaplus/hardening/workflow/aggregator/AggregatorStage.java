package io.github.tlaplus.hardening.workflow.aggregator;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageJobLoop;
import io.github.tlaplus.hardening.workflow.execution.StageWorker;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Joins completed TLC and Apalache results with one in-process worker. */
public final class AggregatorStage implements WorkflowStage {
    private final StageEnvironment environment;
    private final StageCounters counters;
    private final WorkQueue<Path> input;
    private final Map<CorpusStage, OccupancyGate> checkerCapacities;
    private final StageJobLoop<Path> jobs;
    private final WorkerGroup workers = new WorkerGroup("fuzztla-aggregator-");
    private final AtomicInteger recoveredRemaining;
    private final CountDownLatch recoveredDrained;

    public AggregatorStage(
            long recoveredCandidates,
            StageCounters counters,
            StageEnvironment environment,
            WorkQueue<Path> input,
            Map<CorpusStage, OccupancyGate> checkerCapacities) {
        if (recoveredCandidates < 0) {
            throw new IllegalArgumentException("recoveredCandidates must be nonnegative");
        }
        this.environment = Objects.requireNonNull(environment, "environment");
        this.counters = Objects.requireNonNull(counters, "counters");
        this.input = Objects.requireNonNull(input, "input");
        var capacities = new EnumMap<CorpusStage, OccupancyGate>(CorpusStage.class);
        capacities.putAll(Objects.requireNonNull(checkerCapacities, "checkerCapacities"));
        if (!capacities.keySet().equals(java.util.Set.copyOf(CorpusStage.checkerBranches()))) {
            throw new IllegalArgumentException(
                    "checkerCapacities must name every checker branch exactly once");
        }
        this.checkerCapacities = Map.copyOf(capacities);
        recoveredRemaining = new AtomicInteger(Math.toIntExact(recoveredCandidates));
        recoveredDrained = new CountDownLatch(recoveredCandidates == 0 ? 0 : 1);
        jobs = new StageJobLoop<>(
                input,
                environment.cpuBudget(),
                CpuBudget.Priority.AGGREGATOR,
                1,
                counters,
                environment.control());
    }

    @Override
    public String name() {
        return CorpusStage.AGGREGATOR.metadataName();
    }

    @Override
    public void start() {
        workers.start(1, _ -> this::runWorker, recoveredDrained::countDown);
    }

    /** Waits until every pair recovered at startup has been attempted. */
    public void awaitRecovered() throws InterruptedException {
        recoveredDrained.await();
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    @Override
    public void close() {
        input.close();
        workers.close();
    }

    private void runWorker() {
        StageWorker.run(environment.control(), "aggregator worker", () -> {
            try {
                jobs.run(this::aggregate);
            } finally {
                recoveredDrained.countDown();
            }
        });
    }

    private void aggregate(Path candidate) throws Exception {
        try {
            var startTime = Instant.now();
            var found = environment.corpus().aggregationInput(candidate);
            if (found.isEmpty()) {
                return;
            }
            var input = found.orElseThrow();
            var verdict = input.conformanceVerdict();
            environment.corpus().completeAggregation(
                    input,
                    new StageResult(verdict, startTime, StageResult.endedNow(startTime)));
            for (var checker : CorpusStage.checkerBranches()) {
                checkerCapacities.get(checker).release();
            }
            counters.record(verdict);
        } finally {
            completeRecoveredCandidate();
        }
    }

    private void completeRecoveredCandidate() {
        while (true) {
            var remaining = recoveredRemaining.get();
            if (remaining == 0) {
                return;
            }
            if (recoveredRemaining.compareAndSet(remaining, remaining - 1)) {
                if (remaining == 1) {
                    recoveredDrained.countDown();
                }
                return;
            }
        }
    }
}
