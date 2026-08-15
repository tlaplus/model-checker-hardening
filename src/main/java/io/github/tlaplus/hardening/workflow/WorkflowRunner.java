package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/** Runs input generation and parsing concurrently under one shared CPU budget. */
public final class WorkflowRunner {
    private static final Duration PROGRESS_UPDATE_INTERVAL = Duration.ofSeconds(1);

    private final FuzzTlaConfig config;
    private final Generator<TlaEx> generator;

    public WorkflowRunner(FuzzTlaConfig config) {
        this(config, IrGenerators.expressions(
                Objects.requireNonNull(config, "config").generator()));
    }

    WorkflowRunner(FuzzTlaConfig config, Generator<TlaEx> generator) {
        this.config = Objects.requireNonNull(config, "config");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public WorkflowRunSummary run(CorpusDirectory corpus, long seed, int maximumCpus)
            throws IOException, CorpusException, WorkflowException {
        return runInternal(corpus, seed, maximumCpus, null);
    }

    /**
     * Runs the workflow while reporting progress immediately, once per second, and after workers
     * stop. Listener calls never overlap; a listener exception disables further reporting without
     * stopping the workflow.
     */
    public WorkflowRunSummary run(
            CorpusDirectory corpus,
            long seed,
            int maximumCpus,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        return runInternal(
                corpus,
                seed,
                maximumCpus,
                Objects.requireNonNull(progressListener, "progressListener"));
    }

    private WorkflowRunSummary runInternal(
            CorpusDirectory corpus,
            long seed,
            int maximumCpus,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        Objects.requireNonNull(corpus, "corpus");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be nonnegative");
        }
        var availableCpus = Runtime.getRuntime().availableProcessors();
        if (maximumCpus <= 0 || maximumCpus > availableCpus) {
            throw new IllegalArgumentException(
                    "maximumCpus must be in the range 1.." + availableCpus);
        }

        try (var ignored = corpus.acquireLock();
                var parserScratch = corpus.createParserScratch()) {
            var initial = corpus.inventory(generator);
            validateOccupancy(initial);

            var queue = new WorkQueue<Path>();
            for (var path : initial.inputs()) {
                queue.submit(path);
            }
            var control = new WorkflowControl(queue);
            var inputCapacity = new Semaphore(
                    config.workflow().inputs().maximumEntries()
                            - Math.toIntExact(initial.inputEntries()),
                    true);
            var cpuBudget = new CpuBudget(maximumCpus);
            var parser = new ParserStage(
                    config.workflow().parser(),
                    maximumCpus,
                    Math.toIntExact(initial.parserEntries()),
                    corpus,
                    parserScratch.directory(),
                    generator,
                    queue,
                    inputCapacity,
                    cpuBudget,
                    control);
            var pbt = new PbtStage(
                    config.pbt(),
                    config.workflow().maximumEntries(),
                    initial.totalEntries(),
                    corpus,
                    generator,
                    seed,
                    queue,
                    inputCapacity,
                    cpuBudget,
                    control);

            if ((initial.parserEntries() >= config.workflow().parser().maximumEntries()
                            && (initial.inputEntries() > 0
                                    || initial.totalEntries()
                                            < config.workflow().maximumEntries()))
                    || (config.workflow().inputs().maximumEntries() == 0
                            && initial.totalEntries() < config.workflow().maximumEntries())) {
                control.capacityReached();
            }

            try (var progress = progressListener == null
                    ? null
                    : WorkflowProgressMonitor.start(
                            PROGRESS_UPDATE_INTERVAL,
                            () -> progressSnapshot(initial, pbt, parser),
                            progressListener)) {
                try {
                    parser.start();
                    pbt.start();
                    pbt.await();
                    parser.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    control.fail(exception);
                } finally {
                    pbt.close();
                    parser.close();
                }

                if (control.hasFailed()) {
                    var failure = control.failure();
                    if (failure instanceof WorkflowException workflowException) {
                        throw workflowException;
                    }
                    throw new WorkflowException(
                            "workflow stage failed: " + diagnostic(failure), failure);
                }

                var result = corpus.inventory(generator);
                var stopReason = control.state() == WorkflowControl.State.CAPACITY_REACHED
                        ? WorkflowRunSummary.StopReason.CAPACITY_REACHED
                        : WorkflowRunSummary.StopReason.COMPLETED;
                return new WorkflowRunSummary(
                        stopReason, pbt.summary(), parser.summary(), result);
            }
        }
    }

    private static WorkflowProgress progressSnapshot(
            CorpusInventory initial, PbtStage generator, ParserStage parser) {
        var parserSummary = parser.summary();
        var generatorSummary = generator.summary();
        var corpusEntries = generatorSummary.existing() + generatorSummary.added();
        var observedInputs =
                initial.inputEntries() + generatorSummary.added() - parserSummary.processed();
        var remainingInputs = Math.max(0L, Math.min(corpusEntries, observedInputs));
        return new WorkflowProgress(
                generatorSummary, parserSummary, corpusEntries, remainingInputs);
    }

    private void validateOccupancy(CorpusInventory inventory) throws WorkflowException {
        if (inventory.totalEntries() > config.workflow().maximumEntries()) {
            throw new WorkflowException(
                    "corpus contains more entries than workflow.maximum_entries");
        }
        if (inventory.inputEntries() > config.workflow().inputs().maximumEntries()) {
            throw new WorkflowException(
                    "00-inputs exceeds workflow.inputs.maximum_entries");
        }
        if (inventory.parserEntries() > config.workflow().parser().maximumEntries()) {
            throw new WorkflowException(
                    "parser result directories exceed workflow.parser.maximum_entries");
        }
    }

    private static String diagnostic(Throwable exception) {
        if (exception == null) {
            return "unknown failure";
        }
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
