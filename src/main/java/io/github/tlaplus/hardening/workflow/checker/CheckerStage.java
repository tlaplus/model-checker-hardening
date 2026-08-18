package io.github.tlaplus.hardening.workflow.checker;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageJobLoop;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.StageWorker;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.spec.ExprInputToSpec;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Common queue, capacity, scheduling, and corpus logic for model-checker stages. */
public final class CheckerStage implements WorkflowStage {
    private final CheckerBackend backend;
    private final StageEnvironment environment;
    private final StageCounters counters;
    private final OccupancyGate resultCapacity;
    private final StageJobLoop<Path> jobs;
    private final WorkQueue<Path> input;
    private final WorkerGroup workers;

    public CheckerStage(
            CheckerBackend backend,
            long initialOccupancy,
            StageCounters counters,
            StageEnvironment environment,
            WorkQueue<Path> input) {
        this.backend = Objects.requireNonNull(backend, "backend");
        if (backend.workerCount() <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (backend.cpuPermits() <= 0) {
            throw new IllegalArgumentException("cpuPermits must be positive");
        }
        this.environment = Objects.requireNonNull(environment, "environment");
        this.counters = Objects.requireNonNull(counters, "counters");
        this.input = Objects.requireNonNull(input, "input");
        resultCapacity = new OccupancyGate(initialOccupancy, backend.maximumEntries());
        jobs = new StageJobLoop<>(
                input,
                environment.cpuBudget(),
                CpuBudget.Priority.CHECKER,
                backend.cpuPermits(),
                counters,
                environment.control());
        workers = new WorkerGroup("fuzztla-" + backend.name() + "-");
    }

    @Override
    public String name() {
        return backend.name();
    }

    @Override
    public void start() {
        workers.start(backend.workerCount(), _ -> this::runWorker);
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    public StageVerdictSummary summary() {
        return counters.summary();
    }

    @Override
    public void close() {
        input.close();
        workers.close();
    }

    private void runWorker() {
        StageWorker.run(
                environment.control(),
                backend.displayName() + " worker",
                () -> {
                    try (var worker = new Worker()) {
                        jobs.run(worker::check);
                    }
                });
    }

    /**
     * One checker worker. A backend either starts a fresh child process per input or keeps one
     * until it crashes; either way this worker retires its checker after a crash verdict and lets
     * the backend supply a replacement for the next input.
     */
    private final class Worker implements AutoCloseable {
        private CheckerWorker checker;

        private void check(Path path) throws Exception {
            if (!resultCapacity.reserve()) {
                environment.control().capacityReached();
                return;
            }
            var corpus = environment.corpus();
            var startTime = Instant.now();
            var payload = corpus.readCheckerExpressionInput(path);
            var source = ExprInputToSpec.render(
                    backend.displayName(), path, payload, corpus, environment.generator());
            if (checker == null) {
                checker = backend.startWorker();
            }
            var result = checker.check(source);
            if (result.outcome() == StageOutcome.CRASH) {
                checker.close();
                checker = null;
            }
            record(corpus, path, startTime, result);
        }

        @Override
        public void close() {
            if (checker != null) {
                checker.close();
            }
        }
    }

    private void record(
            CorpusDirectory corpus, Path path, Instant startTime, ToolResult result)
            throws Exception {
        var failure = result.failureCode()
                .map(code -> new CheckerFailure(code, backend.failureDetail(result.diagnostic())));
        if (result.outcome() == StageOutcome.FAIL && failure.isEmpty()) {
            throw new WorkflowException(
                    backend.displayName() + " worker returned a failure without a classification");
        }
        corpus.completeChecker(
                path,
                new StageResult(
                        result.outcome().corpusVerdict(),
                        startTime,
                        StageResult.endedNow(startTime),
                        failure,
                        result.diagnostic()));
        counters.record(result.outcome().corpusVerdict());
    }
}
