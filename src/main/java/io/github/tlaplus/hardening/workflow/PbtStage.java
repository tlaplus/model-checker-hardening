package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
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
    private final PbtConfig config;
    private final long maximumEntries;
    private final long initialEntries;
    private final CorpusDirectory corpus;
    private final Generator<?> generator;
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
            Generator<?> generator,
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
        this.summary = new PbtStageSummary(seed, initialEntries, 0, 0, 0, 0);
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
        var maximumAttempts = Math.max(10_000L, 100L * missing);
        var random = new SplittableRandom(seed);
        long added = 0;
        long attempts = 0;
        long rejected = 0;
        long duplicates = 0;

        while (added < missing && !control.shouldStopProducing()) {
            if (attempts >= maximumAttempts) {
                summary = new PbtStageSummary(
                        seed, initialEntries, added, attempts, rejected, duplicates);
                throw new WorkflowException(
                        "could not reach "
                                + maximumEntries
                                + " corpus entries within "
                                + maximumAttempts
                                + " attempts");
            }
            if (!acquireInputCapacity()) {
                break;
            }

            var keepInputSlot = false;
            try {
                if (!cpuBudget.acquire(control::shouldStopProducing)) {
                    break;
                }
                final byte[] input;
                try {
                    attempts++;
                    var length = InputLengthSampler.sample(random, config.maximumInputBytes());
                    input = new byte[length];
                    random.nextBytes(input);
                    try {
                        generator.generate(input);
                    } catch (InputRejectedException exception) {
                        rejected++;
                        continue;
                    } catch (RuntimeException | StackOverflowError exception) {
                        throw generatorCrash(input, attempts, exception);
                    }
                } finally {
                    cpuBudget.release();
                }

                switch (corpus.store(input)) {
                    case ADDED -> {
                        added++;
                        summary = new PbtStageSummary(
                                seed, initialEntries, added, attempts, rejected, duplicates);
                        keepInputSlot = true;
                        output.submit(corpus.inputPath(input));
                    }
                    case DUPLICATE -> duplicates++;
                }
            } finally {
                if (!keepInputSlot) {
                    inputCapacity.release();
                }
                summary = new PbtStageSummary(
                        seed, initialEntries, added, attempts, rejected, duplicates);
            }
        }
    }

    private WorkflowException generatorCrash(byte[] input, long attempt, Throwable failure) {
        var message =
                "input generator crashed at attempt " + attempt + ": " + diagnostic(failure);
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
