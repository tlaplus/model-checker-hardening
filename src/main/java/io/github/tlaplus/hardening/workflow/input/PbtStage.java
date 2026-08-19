package io.github.tlaplus.hardening.workflow.input;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageWorker;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.GeneratorStatistics;
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
    private final StageEnvironment environment;
    private final long seed;
    private final int workerLimit;
    private final WorkQueue<Path> output;
    private final Semaphore inputCapacity;
    private final long missingEntries;
    private final GeneratorStatistics statistics;
    private final AtomicLong nextTarget = new AtomicLong();
    private final WorkerGroup workers = new WorkerGroup("fuzztla-pbt-");

    public PbtStage(
            PbtConfig config,
            long maximumEntries,
            long initialEntries,
            StageEnvironment environment,
            long seed,
            int workerLimit,
            WorkQueue<Path> output,
            Semaphore inputCapacity,
            GeneratorStatistics statistics) {
        this.config = Objects.requireNonNull(config, "config");
        if (initialEntries < 0) {
            throw new IllegalArgumentException("initialEntries must be nonnegative");
        }
        this.initialEntries = initialEntries;
        this.environment = Objects.requireNonNull(environment, "environment");
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
        this.missingEntries = Math.max(0L, maximumEntries - initialEntries);
        this.statistics = Objects.requireNonNull(statistics, "statistics");
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

    public GeneratorSummary summary() {
        return statistics.summary(seed);
    }

    @Override
    public void close() {
        output.close();
        workers.close();
    }

    private void runWorker(int workerId, long workerSeed) {
        StageWorker.run(
                environment.control(),
                "input generator worker " + workerId,
                () -> generateInputs(workerId, workerSeed));
    }

    private void generateInputs(int workerId, long workerSeed) throws Exception {
        var random = new SplittableRandom(workerSeed);
        var cohortRandom = random.split();
        var inputRandom = random.split();

        while (!environment.control().shouldStop()) {
            var target = nextTarget.getAndIncrement();
            if (target >= missingEntries) {
                return;
            }
            var cohort = cohortRandom.nextInt(config.richnessCohorts());
            var threshold = config.richnessThreshold(cohort);
            long entryAttempts = 0;
            var bestRichness = 0.0;

            while (!environment.control().shouldStop()) {
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
                    if (!environment.cpuBudget().acquire(
                            CpuBudget.Priority.GENERATOR,
                            1,
                            environment.control()::shouldStop)) {
                        return;
                    }
                    statistics.elapsed().start();
                    try {
                        final byte[] input;
                        final double richness;
                        try {
                            statistics.recordAttempt();
                            entryAttempts++;
                            var length = InputLengthSampler.sample(
                                    inputRandom, config.maximumInputBytes());
                            input = new byte[length];
                            inputRandom.nextBytes(input);
                            try {
                                var expression = environment.generator().generate(input);
                                richness = CollectionRichness.score(
                                        expression, config.richnessNestingBase());
                            } catch (InputRejectedException exception) {
                                statistics.recordRejection();
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
                            environment.cpuBudget().release(1);
                        }

                        bestRichness = Math.max(bestRichness, richness);
                        if (richness < threshold) {
                            statistics.recordRichnessRejection();
                            continue;
                        }

                        var stored = environment.corpus()
                                .store(input, new GenerationMetadata(cohort, richness));
                        switch (stored) {
                            case ADDED -> {
                                statistics.recordAdmission(richness);
                                keepInputSlot = true;
                                output.submit(environment.corpus().inputPath(input));
                                break;
                            }
                            case DUPLICATE -> statistics.recordDuplicate();
                        }
                    } finally {
                        statistics.elapsed().stop();
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
            var candidate = environment.corpus().recordGeneratorCrash(input, failure);
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
        while (!environment.control().shouldStop()) {
            if (inputCapacity.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }
}
