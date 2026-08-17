package io.github.tlaplus.hardening.workflow.tlc;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.config.TlcStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.spec.ExprInputToSpec;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Concurrent TLC stage backed by a fresh isolated JVM for every input. */
public final class TlcStage implements WorkflowStage {
    private final TlcStageConfig config;
    private final int workerCount;
    private final CorpusDirectory corpus;
    private final Path scratchDirectory;
    private final Generator<TlaEx> generator;
    private final WorkQueue<Path> input;
    private final CpuBudget cpuBudget;
    private final WorkflowControl control;
    private final AtomicInteger occupancy;
    private final LongAdder passed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder crashed = new LongAdder();
    private final WorkerGroup workers = new WorkerGroup("fuzztla-tlc-");

    public TlcStage(
            TlcStageConfig config,
            int workerCount,
            int initialOccupancy,
            CorpusDirectory corpus,
            Path scratchDirectory,
            Generator<TlaEx> generator,
            WorkQueue<Path> input,
            CpuBudget cpuBudget,
            WorkflowControl control) {
        this.config = Objects.requireNonNull(config, "config");
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        this.workerCount = workerCount;
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.input = Objects.requireNonNull(input, "input");
        this.cpuBudget = Objects.requireNonNull(cpuBudget, "cpuBudget");
        this.control = Objects.requireNonNull(control, "control");
        occupancy = new AtomicInteger(initialOccupancy);
    }

    @Override
    public String name() {
        return "tlc";
    }

    @Override
    public void start() {
        workers.start(workerCount, _ -> this::runWorker);
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    public TlcStageSummary summary() {
        return new TlcStageSummary(passed.sum(), failed.sum(), crashed.sum());
    }

    @Override
    public void close() {
        input.close();
        workers.close();
    }

    private void runWorker() {
        try {
            while (!control.shouldStopChecking()) {
                var path = input.take();
                if (path == null) {
                    return;
                }
                if (!cpuBudget.acquire(
                        CpuBudget.Priority.CHECKER,
                        config.workers(),
                        control::shouldStopChecking)) {
                    return;
                }
                try {
                    if (!reserveDestination()) {
                        control.capacityReached();
                        return;
                    }
                    var startTime = Instant.now();
                    var payload = corpus.readTlcExpressionInput(path);
                    var source = ExprInputToSpec.render(
                            "TLC", path, payload, corpus, generator);
                    var result = TlcProcess.check(
                            scratchDirectory, source, config, timeout());
                    var endTime = Instant.now();
                    if (endTime.isBefore(startTime)) {
                        endTime = startTime;
                    }
                    var failure = result.failureCode().map(code -> new CheckerFailure(
                            code, TlcFailureDetail.extract(result.diagnostic())));
                    if (result.outcome() == StageOutcome.FAIL && failure.isEmpty()) {
                        throw new WorkflowException(
                                "TLC worker returned a failure without a classification");
                    }
                    corpus.completeTlc(
                            path,
                            result.outcome().corpusVerdict(),
                            startTime,
                            endTime,
                            failure,
                            result.diagnostic());
                    increment(result.outcome());
                } finally {
                    cpuBudget.release(config.workers());
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!control.shouldStopChecking()) {
                control.fail(new WorkflowException("TLC worker was interrupted", exception));
            }
        } catch (Exception | StackOverflowError exception) {
            control.fail(exception);
        }
    }

    private boolean reserveDestination() {
        while (true) {
            var current = occupancy.get();
            if (current >= config.maximumEntries()) {
                return false;
            }
            if (occupancy.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds());
    }

    private void increment(StageOutcome outcome) {
        switch (outcome) {
            case PASS -> passed.increment();
            case FAIL -> failed.increment();
            case CRASH -> crashed.increment();
        }
    }
}
