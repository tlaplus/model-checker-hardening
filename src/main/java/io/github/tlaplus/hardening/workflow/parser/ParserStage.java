package io.github.tlaplus.hardening.workflow.parser;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.ParserStageConfig;
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
    private final WorkQueue<Path> output;
    private final Semaphore inputCapacity;
    private final CpuBudget cpuBudget;
    private final WorkflowControl control;
    private final AtomicInteger occupancy;
    private final LongAdder passed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder crashed = new LongAdder();
    private final WorkerGroup workers = new WorkerGroup("fuzztla-parser-");

    public ParserStage(
            ParserStageConfig config,
            int workerCount,
            int initialOccupancy,
            CorpusDirectory corpus,
            Path scratchDirectory,
            Generator<TlaEx> generator,
            WorkQueue<Path> input,
            WorkQueue<Path> output,
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
        this.output = Objects.requireNonNull(output, "output");
        this.inputCapacity = Objects.requireNonNull(inputCapacity, "inputCapacity");
        this.cpuBudget = Objects.requireNonNull(cpuBudget, "cpuBudget");
        this.control = Objects.requireNonNull(control, "control");
        this.occupancy = new AtomicInteger(initialOccupancy);
    }

    @Override
    public String name() {
        return "parser";
    }

    @Override
    public void start() {
        workers.start(workerCount, _ -> this::runWorker, output::close);
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    public ParserStageSummary summary() {
        return new ParserStageSummary(passed.sum(), failed.sum(), crashed.sum());
    }

    @Override
    public void close() {
        input.close();
        output.close();
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
                        var tlcInput = corpus.fanOutParserPass(destination);
                        output.submit(tlcInput);
                    }
                } finally {
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
