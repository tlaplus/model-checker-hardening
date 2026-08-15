package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.ParserConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.Generator;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Concurrent parser stage backed by persistent isolated SANY JVMs. */
public final class ParserStage implements WorkflowStage {
    private final ParserConfig config;
    private final int workerCount;
    private final CorpusDirectory corpus;
    private final Path scratchDirectory;
    private final Generator<TlaEx> generator;
    private final WorkQueue<Path> input;
    private final Semaphore inputCapacity;
    private final CpuBudget cpuBudget;
    private final WorkflowControl control;
    private final AtomicInteger occupancy;
    private final LongAdder passed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder crashed = new LongAdder();
    private final List<Thread> workers = new ArrayList<>();

    ParserStage(
            ParserConfig config,
            int workerCount,
            int initialOccupancy,
            CorpusDirectory corpus,
            Path scratchDirectory,
            Generator<TlaEx> generator,
            WorkQueue<Path> input,
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
    public synchronized void start() {
        if (!workers.isEmpty()) {
            throw new IllegalStateException("parser stage has already started");
        }
        for (var index = 0; index < workerCount; index++) {
            workers.add(Thread.ofPlatform()
                    .name("fuzztla-parser-" + index)
                    .start(this::runWorker));
        }
    }

    @Override
    public void await() throws InterruptedException {
        List<Thread> snapshot;
        synchronized (this) {
            if (workers.isEmpty()) {
                throw new IllegalStateException("parser stage has not started");
            }
            snapshot = List.copyOf(workers);
        }
        for (var worker : snapshot) {
            worker.join();
        }
    }

    public ParserStageSummary summary() {
        return new ParserStageSummary(passed.sum(), failed.sum(), crashed.sum());
    }

    @Override
    public void close() {
        input.close();
        final List<Thread> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(workers);
        }
        for (var worker : snapshot) {
            if (worker.isAlive()) {
                worker.interrupt();
            }
        }
        var interrupted = false;
        for (var worker : snapshot) {
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                    worker.interrupt();
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void runWorker() {
        ParserProcess process = null;
        try {
            while (!control.shouldAbortParsing()) {
                var path = input.take();
                if (path == null) {
                    return;
                }
                if (!reserveDestination()) {
                    control.capacityReached();
                    return;
                }

                if (!cpuBudget.acquire(control::hasFailed)) {
                    return;
                }
                try {
                    var startTime = Instant.now();
                    var payload = corpus.readExpressionInput(path);
                    var expression = generator.generate(payload);
                    var module = FuzzInputModule.create(expression);
                    var source = PrettyWriter.writeAsString(
                            module, TlaWriter$.MODULE$.STANDARD_MODULES());
                    if (process == null) {
                        process = ParserProcess.start(scratchDirectory, timeout());
                    }
                    var result = process.parse(source, timeout());
                    var endTime = Instant.now();
                    if (endTime.isBefore(startTime)) {
                        endTime = startTime;
                    }
                    corpus.completeParser(
                            path,
                            result.outcome().corpusVerdict(),
                            startTime,
                            endTime,
                            result.diagnostic());
                    inputCapacity.release();
                    increment(result.outcome());
                    if (result.outcome() == ParserResult.Outcome.CRASH) {
                        process = null;
                    }
                } finally {
                    cpuBudget.release();
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

    private void increment(ParserResult.Outcome outcome) {
        switch (outcome) {
            case PASS -> passed.increment();
            case FAIL -> failed.increment();
            case CRASH -> crashed.increment();
        }
    }
}
