package io.github.tlaplus.hardening.workflow.input;

import at.forsyte.apalache.tla.lir.TlaEx;
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
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

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
    private final AtomicLong nextTarget = new AtomicLong();
    private final LongAdder attempts = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder richnessRejected = new LongAdder();
    private final LongAdder duplicates = new LongAdder();
    private final Object admissionLock = new Object();
    private final WorkerGroup workers = new WorkerGroup("fuzztla-pbt-");

    private long added;
    private double minimumRichness;
    private double maximumRichness;
    private double averageRichness;

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
            WorkflowControl control) {
        this.config = Objects.requireNonNull(config, "config");
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
        synchronized (admissionLock) {
            return new PbtStageSummary(
                    seed,
                    initialEntries,
                    added,
                    attempts.sum(),
                    rejected.sum(),
                    richnessRejected.sum(),
                    duplicates.sum(),
                    minimumRichness,
                    maximumRichness,
                    averageRichness);
        }
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
                    if (!cpuBudget.acquire(1, control::shouldStopProducing)) {
                        return;
                    }
                    final byte[] input;
                    final double richness;
                    try {
                        attempts.increment();
                        entryAttempts++;
                        var length =
                                InputLengthSampler.sample(inputRandom, config.maximumInputBytes());
                        input = new byte[length];
                        inputRandom.nextBytes(input);
                        try {
                            var expression = generator.generate(input);
                            richness = CollectionRichness.score(
                                    expression, config.richnessNestingBase());
                        } catch (InputRejectedException exception) {
                            rejected.increment();
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
                        richnessRejected.increment();
                        continue;
                    }

                    switch (corpus.store(input, new GenerationMetadata(cohort, richness))) {
                        case ADDED -> {
                            recordAdmission(richness);
                            keepInputSlot = true;
                            output.submit(corpus.inputPath(input));
                            break;
                        }
                        case DUPLICATE -> duplicates.increment();
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
                + diagnostic(failure);
        try {
            var candidate = corpus.recordGeneratorCrash(input, failure);
            message += "; candidate saved to '" + candidate + "'";
        } catch (IOException | CorpusException | RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            message += "; crash artifact could not be saved: " + diagnostic(recordingFailure);
        }
        return new WorkflowException(message, failure);
    }

    private void recordAdmission(double richness) {
        synchronized (admissionLock) {
            var nextAdded = added + 1;
            if (added == 0) {
                minimumRichness = richness;
                maximumRichness = richness;
                averageRichness = richness;
            } else {
                minimumRichness = Math.min(minimumRichness, richness);
                maximumRichness = Math.max(maximumRichness, richness);
                averageRichness += (richness - averageRichness) / nextAdded;
                averageRichness = Math.max(
                        minimumRichness, Math.min(maximumRichness, averageRichness));
            }
            added = nextAdded;
        }
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

    private static String diagnostic(Throwable failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
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
