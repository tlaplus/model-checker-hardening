package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.pbt.InputLengthSampler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** The single-worker property-based input generation stage. */
public final class PbtStage implements WorkflowStage {
    private static final long MAXIMUM_ATTEMPTS_PER_ENTRY = 10_000;

    private final PbtConfig config;
    private final long maximumEntries;
    private final long initialEntries;
    private final CorpusDirectory corpus;
    private final Generator<TlaEx> generator;
    private final long seed;
    private final WorkQueue<Path> output;
    private final Semaphore inputCapacity;
    private final CpuBudget cpuBudget;
    private final WorkflowControl control;

    private volatile PbtStageSummary summary;
    private Thread worker;

    PbtStage(
            PbtConfig config,
            long maximumEntries,
            long initialEntries,
            CorpusDirectory corpus,
            Generator<TlaEx> generator,
            long seed,
            WorkQueue<Path> output,
            Semaphore inputCapacity,
            CpuBudget cpuBudget,
            WorkflowControl control) {
        this.config = Objects.requireNonNull(config, "config");
        this.maximumEntries = maximumEntries;
        this.initialEntries = initialEntries;
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.seed = seed;
        this.output = Objects.requireNonNull(output, "output");
        this.inputCapacity = Objects.requireNonNull(inputCapacity, "inputCapacity");
        this.cpuBudget = Objects.requireNonNull(cpuBudget, "cpuBudget");
        this.control = Objects.requireNonNull(control, "control");
        this.summary = new PbtStageSummary(seed, initialEntries, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
    }

    @Override
    public String name() {
        return "inputs";
    }

    @Override
    public synchronized void start() {
        if (worker != null) {
            throw new IllegalStateException("PBT stage has already started");
        }
        worker = Thread.ofPlatform().name("fuzztla-pbt").start(this::runWorker);
    }

    @Override
    public void await() throws InterruptedException {
        var current = worker;
        if (current == null) {
            throw new IllegalStateException("PBT stage has not started");
        }
        current.join();
    }

    public PbtStageSummary summary() {
        return summary;
    }

    @Override
    public void close() {
        var current = worker;
        if (current != null && current.isAlive()) {
            current.interrupt();
        }
        output.close();
    }

    private void runWorker() {
        try {
            generateInputs();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!control.shouldStopProducing()) {
                control.fail(new WorkflowException("input generation was interrupted", exception));
            }
        } catch (Exception | StackOverflowError exception) {
            control.fail(exception);
        } finally {
            output.close();
        }
    }

    private void generateInputs() throws Exception {
        var missing = Math.max(0L, maximumEntries - initialEntries);
        var random = new SplittableRandom(seed);
        var cohortRandom = random.split();
        var inputRandom = random.split();
        long added = 0;
        long attempts = 0;
        long rejected = 0;
        long richnessRejected = 0;
        long duplicates = 0;
        var minimumRichness = 0.0;
        var maximumRichness = 0.0;
        var averageRichness = 0.0;

        generation:
        while (added < missing && !control.shouldStopProducing()) {
            var cohort = cohortRandom.nextInt(config.richnessCohorts());
            var threshold = config.richnessThreshold(cohort);
            long entryAttempts = 0;
            var bestRichness = 0.0;

            while (!control.shouldStopProducing()) {
                if (entryAttempts >= MAXIMUM_ATTEMPTS_PER_ENTRY) {
                    summary = new PbtStageSummary(
                            seed,
                            initialEntries,
                            added,
                            attempts,
                            rejected,
                            richnessRejected,
                            duplicates,
                            minimumRichness,
                            maximumRichness,
                            averageRichness);
                    throw new WorkflowException(
                            "could not generate corpus entry "
                                    + (initialEntries + added + 1)
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
                    break generation;
                }

                var keepInputSlot = false;
                try {
                    if (!cpuBudget.acquire(control::shouldStopProducing)) {
                        break generation;
                    }
                    final byte[] input;
                    final double richness;
                    try {
                        attempts++;
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
                            rejected++;
                            continue;
                        } catch (RuntimeException | StackOverflowError exception) {
                            throw generatorCrash(input, attempts, exception);
                        }
                    } finally {
                        cpuBudget.release();
                    }

                    bestRichness = Math.max(bestRichness, richness);
                    if (richness < threshold) {
                        richnessRejected++;
                        continue;
                    }

                    switch (corpus.store(input, new GenerationMetadata(cohort, richness))) {
                        case ADDED -> {
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
                                        minimumRichness,
                                        Math.min(maximumRichness, averageRichness));
                            }
                            added = nextAdded;
                            summary = new PbtStageSummary(
                                    seed,
                                    initialEntries,
                                    added,
                                    attempts,
                                    rejected,
                                    richnessRejected,
                                    duplicates,
                                    minimumRichness,
                                    maximumRichness,
                                    averageRichness);
                            keepInputSlot = true;
                            output.submit(corpus.inputPath(input));
                            break;
                        }
                        case DUPLICATE -> duplicates++;
                    }
                } finally {
                    if (!keepInputSlot) {
                        inputCapacity.release();
                    }
                    summary = new PbtStageSummary(
                            seed,
                            initialEntries,
                            added,
                            attempts,
                            rejected,
                            richnessRejected,
                            duplicates,
                            minimumRichness,
                            maximumRichness,
                            averageRichness);
                }
                if (keepInputSlot) {
                    break;
                }
            }
        }
    }

    private WorkflowException generatorCrash(byte[] input, long attempt, Throwable failure) {
        var message = "input generator crashed at attempt " + attempt + ": " + diagnostic(failure);
        try {
            var candidate = corpus.recordGeneratorCrash(input, failure);
            message += "; candidate saved to '" + candidate + "'";
        } catch (IOException | CorpusException | RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            message += "; crash artifact could not be saved: " + diagnostic(recordingFailure);
        }
        return new WorkflowException(message, failure);
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
