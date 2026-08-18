package io.github.tlaplus.hardening.workflow.parser;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusPath;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Concurrent parser stage backed by persistent isolated SANY JVMs. */
public final class ParserStage implements WorkflowStage {
    private final ParserStageConfig config;
    private final int workerCount;
    private final CorpusDirectory corpus;
    private final Path scratchDirectory;
    private final Generator<TlaEx> generator;
    private final WorkQueue<Path> input;
    private final WorkQueue<Path> tlcOutput;
    private final WorkQueue<Path> apalacheOutput;
    private final Semaphore inputCapacity;
    private final CpuBudget cpuBudget;
    private final WorkflowControl control;
    private final AtomicInteger occupancy;
    private final ElapsedTimeAccumulator elapsed;
    private final LongAdder passed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder crashed = new LongAdder();
    private final WorkerGroup workers = new WorkerGroup("fuzztla-parser-");

    public ParserStage(
            ParserStageConfig config,
            int workerCount,
            ParserStageSummary initialStatistics,
            ElapsedTimeAccumulator elapsed,
            CorpusDirectory corpus,
            Path scratchDirectory,
            Generator<TlaEx> generator,
            WorkQueue<Path> input,
            WorkQueue<Path> tlcOutput,
            WorkQueue<Path> apalacheOutput,
            Semaphore inputCapacity,
            CpuBudget cpuBudget,
            WorkflowControl control) {
        this.config = Objects.requireNonNull(config, "config");
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        this.workerCount = workerCount;
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.scratchDirectory =
                Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.input = Objects.requireNonNull(input, "input");
        this.tlcOutput = Objects.requireNonNull(tlcOutput, "tlcOutput");
        this.apalacheOutput = Objects.requireNonNull(apalacheOutput, "apalacheOutput");
        this.inputCapacity = Objects.requireNonNull(inputCapacity, "inputCapacity");
        this.cpuBudget = Objects.requireNonNull(cpuBudget, "cpuBudget");
        this.control = Objects.requireNonNull(control, "control");
        Objects.requireNonNull(initialStatistics, "initialStatistics");
        this.occupancy = new AtomicInteger(
                Math.toIntExact(initialStatistics.failed() + initialStatistics.crashed()));
        passed.add(initialStatistics.passed());
        failed.add(initialStatistics.failed());
        crashed.add(initialStatistics.crashed());
        this.elapsed = Objects.requireNonNull(elapsed, "elapsed");
    }

    @Override
    public String name() {
        return "parser";
    }

    @Override
    public void start() {
        workers.start(workerCount, _ -> this::runWorker, () -> {
            tlcOutput.close();
            apalacheOutput.close();
        });
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    public ParserStageSummary summary() {
        return new ParserStageSummary(passed.sum(), failed.sum(), crashed.sum(), elapsed.elapsed());
    }

    @Override
    public void close() {
        input.close();
        tlcOutput.close();
        apalacheOutput.close();
        workers.close();
    }

    private void runWorker() {
        ParserProcess process = null;
        try {
            while (!control.shouldAbortParsing()) {
                var path = input.take();
                if (path == null) {
                    return;
                }

                if (!cpuBudget.acquire(
                        CpuBudget.Priority.PARSER, 1, control::shouldAbortParsing)) {
                    return;
                }
                elapsed.start();
                try {
                    var startTime = Instant.now();
                    var payload = corpus.readExpressionInput(path);
                    var source = ExprInputToSpec.render(
                            "parser", path, payload, corpus, generator);
                    if (process == null) {
                        process = ParserProcess.start(scratchDirectory, timeout());
                    }
                    var result = process.parse(source, timeout());
                    if (result.outcome() == StageOutcome.CRASH) {
                        process = null;
                    }
                    if (result.outcome() != StageOutcome.PASS && !reserveDestination()) {
                        control.capacityReached();
                        return;
                    }
                    var endTime = Instant.now();
                    if (endTime.isBefore(startTime)) {
                        endTime = startTime;
                    }
                    var destination = corpus.completeParser(
                            path,
                            result.outcome().corpusVerdict(),
                            startTime,
                            endTime,
                            result.diagnostic());
                    inputCapacity.release();
                    increment(result.outcome());
                    if (result.outcome() == StageOutcome.PASS) {
                        var inputName = destination.getFileName();
                        corpus.fanOutParserPass(destination);
                        tlcOutput.submit(corpus.resolve(CorpusPath.TLC_INPUT).resolve(inputName));
                        apalacheOutput.submit(
                                corpus.resolve(CorpusPath.APALACHE_INPUT).resolve(inputName));
                    }
                } finally {
                    elapsed.stop();
                    cpuBudget.release(1);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!control.shouldAbortParsing()) {
                control.fail(new WorkflowException("parser worker was interrupted", exception));
            }
        } catch (Exception | StackOverflowError exception) {
            control.fail(exception);
        } finally {
            if (process != null) {
                process.close();
            }
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
