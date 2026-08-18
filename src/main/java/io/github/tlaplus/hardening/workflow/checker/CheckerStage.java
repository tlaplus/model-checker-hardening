package io.github.tlaplus.hardening.workflow.checker;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.spec.ExprInputToSpec;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Common queue, capacity, scheduling, and corpus logic for model-checker stages. */
public final class CheckerStage implements WorkflowStage {
    private final CheckerBackend backend;
    private final CorpusDirectory corpus;
    private final Generator<TlaEx> generator;
    private final WorkQueue<Path> input;
    private final CpuBudget cpuBudget;
    private final WorkflowControl control;
    private final AtomicInteger occupancy;
    private final ElapsedTimeAccumulator elapsed;
    private final LongAdder passed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder crashed = new LongAdder();
    private final WorkerGroup workers;

    public CheckerStage(
            CheckerBackend backend,
            CheckerStageSummary initialStatistics,
            ElapsedTimeAccumulator elapsed,
            CorpusDirectory corpus,
            Generator<TlaEx> generator,
            WorkQueue<Path> input,
            CpuBudget cpuBudget,
            WorkflowControl control) {
        this.backend = Objects.requireNonNull(backend, "backend");
        if (backend.workerCount() <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (backend.cpuPermits() <= 0) {
            throw new IllegalArgumentException("cpuPermits must be positive");
        }
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.input = Objects.requireNonNull(input, "input");
        this.cpuBudget = Objects.requireNonNull(cpuBudget, "cpuBudget");
        this.control = Objects.requireNonNull(control, "control");
        Objects.requireNonNull(initialStatistics, "initialStatistics");
        occupancy = new AtomicInteger(Math.toIntExact(initialStatistics.processed()));
        passed.add(initialStatistics.passed());
        failed.add(initialStatistics.failed());
        crashed.add(initialStatistics.crashed());
        this.elapsed = Objects.requireNonNull(elapsed, "elapsed");
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

    public CheckerStageSummary summary() {
        return new CheckerStageSummary(passed.sum(), failed.sum(), crashed.sum(), elapsed.elapsed());
    }

    @Override
    public void close() {
        input.close();
        workers.close();
    }

    private void runWorker() {
        var displayName = backend.displayName();
        CheckerWorker checker = null;
        try {
            while (!control.shouldStopChecking()) {
                var path = input.take();
                if (path == null) {
                    return;
                }
                if (!cpuBudget.acquire(
                        CpuBudget.Priority.CHECKER,
                        backend.cpuPermits(),
                        control::shouldStopChecking)) {
                    return;
                }
                elapsed.start();
                try {
                    if (!reserveDestination()) {
                        control.capacityReached();
                        return;
                    }
                    var startTime = Instant.now();
                    var payload = corpus.readCheckerExpressionInput(path);
                    var source = ExprInputToSpec.render(
                            displayName, path, payload, corpus, generator);
                    if (checker == null) {
                        checker = backend.startWorker();
                    }
                    var result = checker.check(source);
                    if (result.outcome() == StageOutcome.CRASH) {
                        checker.close();
                        checker = null;
                    }
                    var endTime = Instant.now();
                    if (endTime.isBefore(startTime)) {
                        endTime = startTime;
                    }
                    var failure = result.failureCode().map(code -> new CheckerFailure(
                            code, backend.failureDetail(result.diagnostic())));
                    if (result.outcome() == StageOutcome.FAIL && failure.isEmpty()) {
                        throw new WorkflowException(displayName
                                + " worker returned a failure without a classification");
                    }
                    corpus.completeChecker(
                            path,
                            result.outcome().corpusVerdict(),
                            startTime,
                            endTime,
                            failure,
                            result.diagnostic());
                    increment(result.outcome());
                } finally {
                    elapsed.stop();
                    cpuBudget.release(backend.cpuPermits());
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!control.shouldStopChecking()) {
                control.fail(new WorkflowException(
                        displayName + " worker was interrupted", exception));
            }
        } catch (Exception | StackOverflowError exception) {
            control.fail(exception);
        } finally {
            if (checker != null) {
                checker.close();
            }
        }
    }

    private boolean reserveDestination() {
        while (true) {
            var current = occupancy.get();
            if (current >= backend.maximumEntries()) {
                return false;
            }
            if (occupancy.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void increment(StageOutcome outcome) {
        switch (outcome) {
            case PASS -> passed.increment();
            case FAIL -> failed.increment();
            case CRASH -> crashed.increment();
        }
    }
}
