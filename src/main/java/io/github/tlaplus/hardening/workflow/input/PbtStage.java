package io.github.tlaplus.hardening.workflow.input;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Concurrent property-based input generation stage. */
public final class PbtStage implements WorkflowStage {
    private static final long MAXIMUM_ATTEMPTS_PER_ENTRY = 10_000;

    private final PbtConfig config;
    private final long initialEntries;
    private final CorpusDirectory corpus;
    private final Generator<TlaEx> generator;
    private final long seed;
    private final int workerLimit;
    private final WorkQueue<Path> output;
    private final Semaphore inputCapacity;
    private final CpuBudget cpuBudget;
    private final WorkflowControl control;
    private final long missingEntries;
    private final WorkflowMetrics metrics;
    private final AtomicLong nextTarget = new AtomicLong();
    private final WorkerGroup workers = new WorkerGroup("fuzztla-pbt-");

    public PbtStage(
            PbtConfig config,
            long maximumEntries,
            long initialEntries,
            CorpusDirectory corpus,
            Generator<TlaEx> generator,
            long seed,
            int workerLimit,
            WorkQueue<Path> output,
            Semaphore inputCapacity,
            CpuBudget cpuBudget,
            WorkflowControl control,
            WorkflowMetrics metrics) {
        this.config = Objects.requireNonNull(config, "config");
        if (initialEntries < 0) {
            throw new IllegalArgumentException("initialEntries must be nonnegative");
        }
        this.initialEntries = initialEntries;
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.generator = Objects.requireNonNull(generator, "generator");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be nonnegative");
        }
        this.seed = seed;
        if (workerLimit <= 0) {
            throw new IllegalArgumentException("workerLimit must be positive");
        }
        this.workerLimit = workerLimit;
        this.output = Objects.requireNonNull(output, "output");
        this.inputCapacity = Objects.requireNonNull(inputCapacity, "inputCapacity");
        this.cpuBudget = Objects.requireNonNull(cpuBudget, "cpuBudget");
        this.control = Objects.requireNonNull(control, "control");
        this.missingEntries = Math.max(0L, maximumEntries - initialEntries);
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public String name() {
        return "inputs";
    }

    @Override
    public void start() {
        var workerCount = Math.toIntExact(Math.min(workerLimit, missingEntries));
        var workerSeeds = workerSeeds(seed, workerCount);
        workers.start(
                workerCount,
                workerId -> () -> runWorker(workerId, workerSeeds[workerId]),
                output::close);
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    public PbtStageSummary summary() {
        return metrics.generatorSummary(seed);
    }

    @Override
    public void close() {
        output.close();
        workers.close();
    }

    private void runWorker(int workerId, long workerSeed) {
        try {
            generateInputs(workerId, workerSeed);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!control.shouldStopProducing()) {
                control.fail(new WorkflowException(
                        "input generator worker " + workerId + " was interrupted", exception));
            }
        } catch (Exception | StackOverflowError exception) {
            control.fail(exception);
        }
    }

    private void generateInputs(int workerId, long workerSeed) throws Exception {
        var random = new SplittableRandom(workerSeed);
        var cohortRandom = random.split();
        var inputRandom = random.split();

        while (!control.shouldStopProducing()) {
            var target = nextTarget.getAndIncrement();
            if (target >= missingEntries) {
                return;
            }
            var cohort = cohortRandom.nextInt(config.richnessCohorts());
            var threshold = config.richnessThreshold(cohort);
            long entryAttempts = 0;
            var bestRichness = 0.0;

            while (!control.shouldStopProducing()) {
                if (entryAttempts >= MAXIMUM_ATTEMPTS_PER_ENTRY) {
                    throw new WorkflowException(
                            "input generator worker "
                                    + workerId
                                    + " (seed "
                                    + workerSeed
                                    + ") could not generate corpus entry "
                                    + (initialEntries + target + 1)
                                    + " for richness cohort "
                                    + cohort
                                    + " (threshold "
                                    + threshold
                                    + ") within "
                                    + MAXIMUM_ATTEMPTS_PER_ENTRY
                                    + " attempts; best richness was "
                                    + bestRichness);
                }
                if (!acquireInputCapacity()) {
                    return;
                }

                var keepInputSlot = false;
                try {
                    if (!cpuBudget.acquire(
                            CpuBudget.Priority.GENERATOR,
                            1,
                            control::shouldStopProducing)) {
                        return;
                    }
                    metrics.generatorElapsed().start();
                    try {
                        final byte[] input;
                        final double richness;
                        try {
                            metrics.recordGeneratorAttempt();
                            entryAttempts++;
                            var length = InputLengthSampler.sample(
                                    inputRandom, config.maximumInputBytes());
                            input = new byte[length];
                            inputRandom.nextBytes(input);
                            try {
                                var expression = generator.generate(input);
                                richness = CollectionRichness.score(
                                        expression, config.richnessNestingBase());
                            } catch (InputRejectedException exception) {
                                metrics.recordGeneratorRejection();
                                continue;
                            } catch (RuntimeException | StackOverflowError exception) {
                                throw generatorCrash(
                                        input,
                                        workerId,
                                        workerSeed,
                                        target,
                                        entryAttempts,
                                        exception);
                            }
                        } finally {
                            cpuBudget.release(1);
                        }

                        bestRichness = Math.max(bestRichness, richness);
                        if (richness < threshold) {
                            metrics.recordRichnessRejection();
                            continue;
                        }

                        switch (corpus.store(input, new GenerationMetadata(cohort, richness))) {
                            case ADDED -> {
                                metrics.recordAdmission(richness);
                                keepInputSlot = true;
                                output.submit(corpus.inputPath(input));
                                break;
                            }
                            case DUPLICATE -> metrics.recordDuplicate();
                        }
                    } finally {
                        metrics.generatorElapsed().stop();
                    }
                } finally {
                    if (!keepInputSlot) {
                        inputCapacity.release();
                    }
                }
                if (keepInputSlot) {
                    break;
                }
            }
        }
    }

    private WorkflowException generatorCrash(
            byte[] input,
            int workerId,
            long workerSeed,
            long target,
            long targetAttempt,
            Throwable failure) {
        var message = "input generator worker "
                + workerId
                + " (seed "
                + workerSeed
                + ") crashed while generating corpus entry "
                + (initialEntries + target + 1)
                + " at target attempt "
                + targetAttempt
                + ": "
                + Diagnostics.message(failure);
        try {
            var candidate = corpus.recordGeneratorCrash(input, failure);
            message += "; candidate saved to '" + candidate + "'";
        } catch (IOException | CorpusException | RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            message += "; crash artifact could not be saved: "
                    + Diagnostics.message(recordingFailure);
        }
        return new WorkflowException(message, failure);
    }

    static long[] workerSeeds(long seed, int workerCount) {
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be nonnegative");
        }
        if (workerCount < 0) {
            throw new IllegalArgumentException("workerCount must be nonnegative");
        }
        var seeds = new long[workerCount];
        var source = new SplittableRandom(seed);
        for (var workerId = 0; workerId < workerCount; workerId++) {
            seeds[workerId] = source.nextLong();
        }
        return seeds;
    }

    private boolean acquireInputCapacity() throws InterruptedException {
        while (!control.shouldStopProducing()) {
            if (inputCapacity.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }
}
