package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.checker.CheckerStage;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import io.github.tlaplus.hardening.workflow.execution.WorkflowProgressMonitor;
import io.github.tlaplus.hardening.workflow.input.PbtStage;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import io.github.tlaplus.hardening.workflow.parser.ParserStage;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Runs input generation, parsing, TLC, and Apalache under one shared CPU budget. */
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
        var invocationElapsed = new ElapsedTimeAccumulator();
        invocationElapsed.start();
        Objects.requireNonNull(corpus, "corpus");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be nonnegative");
        }
        var availableCpus = Runtime.getRuntime().availableProcessors();
        if (maximumCpus <= 0 || maximumCpus > availableCpus) {
            throw new IllegalArgumentException(
                    "maximumCpus must be in the range 1.." + availableCpus);
        }
        if (config.workflow().tlc().workers() > maximumCpus) {
            throw new WorkflowException(
                    "workflow.tlc.workers must not exceed run --max-cpus");
        }
        if (config.workflow().apalache().workers() > maximumCpus) {
            throw new WorkflowException(
                    "workflow.apalache.workers must not exceed run --max-cpus");
        }
        var apalacheJar = ApalacheDistribution.locate();

        try (var corpusLock = corpus.acquireExclusiveLock()) {
            var initial = corpus.recoverAndValidate(entryValidator());
            validateOccupancy(initial);
            var metrics = new WorkflowMetrics(corpus.readRunStatistics(), initial.totalEntries());
            var statistics = new StatisticsOnExit(corpus, metrics, invocationElapsed);
            StageRunResult result;
            try (statistics) {
                try (var parserScratch = corpus.createParserScratch();
                        var tlcScratch = corpus.createTlcScratch();
                        var apalacheScratch = corpus.createApalacheScratch()) {
                    result = runStages(
                            corpus,
                            seed,
                            maximumCpus,
                            parserScratch.directory(),
                            tlcScratch.directory(),
                            apalacheScratch.directory(),
                            apalacheJar,
                            initial,
                            metrics,
                            invocationElapsed,
                            progressListener);
                }
            }
            return result.summary(statistics.totalElapsed());
        }
    }

    private StageRunResult runStages(
            CorpusDirectory corpus,
            long seed,
            int maximumCpus,
            Path parserScratch,
            Path tlcScratch,
            Path apalacheScratch,
            Path apalacheJar,
            CorpusInventory initial,
            WorkflowMetrics metrics,
            ElapsedTimeAccumulator invocationElapsed,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        var parserQueue = new WorkQueue<Path>();
        initial.inputs().forEach(parserQueue::submit);
        var tlcQueue = new WorkQueue<Path>();
        initial.tlcInputs().forEach(tlcQueue::submit);
        var apalacheQueue = new WorkQueue<Path>();
        initial.apalacheInputs().forEach(apalacheQueue::submit);
        var control = new WorkflowControl(parserQueue, tlcQueue, apalacheQueue);
        var inputCapacity = new Semaphore(
                config.workflow().inputs().maximumEntries()
                        - Math.toIntExact(initial.inputEntries()),
                true);
        var cpuBudget = new CpuBudget(maximumCpus);
        var environment = new StageEnvironment(corpus, generator, cpuBudget, control);
        var initialParser = new StageVerdictSummary(
                initial.parserPassEntries(),
                initial.parserFailEntries(),
                initial.parserCrashEntries(),
                metrics.parserElapsed().elapsed());
        var initialTlc = new StageVerdictSummary(
                initial.tlcPassEntries(),
                initial.tlcFailEntries(),
                initial.tlcCrashEntries(),
                metrics.tlcElapsed().elapsed());
        var initialApalache = new StageVerdictSummary(
                initial.apalachePassEntries(),
                initial.apalacheFailEntries(),
                initial.apalacheCrashEntries(),
                metrics.apalacheElapsed().elapsed());
        var parser = new ParserStage(
                config.workflow().parser(),
                maximumCpus,
                initialParser,
                new StageCounters(initialParser, metrics.parserElapsed()),
                environment,
                parserScratch,
                parserQueue,
                tlcQueue,
                apalacheQueue,
                inputCapacity);
        var tlc = new CheckerStage(
                new TlcCheckerBackend(
                        config.workflow().tlc(),
                        maximumCpus / config.workflow().tlc().workers(),
                        tlcScratch),
                initialTlc,
                new StageCounters(initialTlc, metrics.tlcElapsed()),
                environment,
                tlcQueue);
        var apalache = new CheckerStage(
                new ApalacheCheckerBackend(
                        config.workflow().apalache(), apalacheJar, apalacheScratch),
                initialApalache,
                new StageCounters(initialApalache, metrics.apalacheElapsed()),
                environment,
                apalacheQueue);
        var pbt = new PbtStage(
                config.pbt(),
                config.workflow().maximumEntries(),
                initial.totalEntries(),
                environment,
                seed,
                maximumCpus,
                parserQueue,
                inputCapacity,
                metrics);
        var progressPhase = new AtomicReference<>(WorkflowProgress.Phase.RUNNING);

        if (capacityIsAlreadyExhausted(initial)) {
            control.capacityReached();
        }

        try (var progress = progressListener == null
                ? null
                : WorkflowProgressMonitor.start(
                        PROGRESS_UPDATE_INTERVAL,
                        () -> progressSnapshot(
                                progressPhase.get(),
                                initial,
                                pbt,
                                parser,
                                tlc,
                                apalache,
                                metrics,
                                invocationElapsed),
                        progressListener)) {
            try {
                tlc.start();
                apalache.start();
                parser.start();
                pbt.start();
                pbt.await();
                parser.await();
                tlc.await();
                apalache.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                control.fail(exception);
            } finally {
                pbt.close();
                parser.close();
                tlc.close();
                apalache.close();
            }

            throwIfFailed(control);
            progressPhase.set(WorkflowProgress.Phase.FINALIZING);
            var result = corpus.recoverAndValidate(entryValidator());
            var stopReason = control.state() == WorkflowControl.State.CAPACITY_REACHED
                    ? WorkflowRunSummary.StopReason.CAPACITY_REACHED
                    : WorkflowRunSummary.StopReason.COMPLETED;
            return new StageRunResult(
                    stopReason,
                    pbt.summary(),
                    parserSummary(result, parser.summary().elapsed()),
                    tlcSummary(result, tlc.summary().elapsed()),
                    apalacheSummary(result, apalache.summary().elapsed()),
                    result);
        }
    }

    /**
     * Returns the policy that decides whether a stored payload is still usable: it must decode to an
     * expression under this run's generator configuration.
     */
    private CorpusEntryValidator entryValidator() {
        return (entry, input) -> {
            try {
                generator.generate(input);
            } catch (InputRejectedException exception) {
                throw new CorpusException(
                        "corpus entry is rejected: "
                                + entry
                                + ": "
                                + Diagnostics.message(exception),
                        exception);
            }
        };
    }

    private boolean capacityIsAlreadyExhausted(CorpusInventory initial) {
        return (initial.tlcEntries() >= config.workflow().tlc().maximumEntries()
                        && initial.tlcInputEntries() > 0)
                || (initial.apalacheEntries()
                                >= config.workflow().apalache().maximumEntries()
                        && initial.apalacheInputEntries() > 0)
                || (config.workflow().inputs().maximumEntries() == 0
                        && initial.totalEntries() < config.workflow().maximumEntries());
    }

    private static void throwIfFailed(WorkflowControl control) throws WorkflowException {
        if (!control.hasFailed()) {
            return;
        }
        var failure = control.failure();
        if (failure instanceof WorkflowException workflowException) {
            throw workflowException;
        }
        throw new WorkflowException(
                "workflow stage failed: " + Diagnostics.message(failure), failure);
    }

    private static WorkflowProgress progressSnapshot(
            WorkflowProgress.Phase phase,
            CorpusInventory initial,
            PbtStage generator,
            ParserStage parser,
            CheckerStage tlc,
            CheckerStage apalache,
            WorkflowMetrics metrics,
            ElapsedTimeAccumulator invocationElapsed) {
        var parserSummary = parser.summary();
        var tlcSummary = tlc.summary();
        var apalacheSummary = apalache.summary();
        var generatorSummary = generator.summary();
        var corpusEntries = generatorSummary.generated();
        var generatedThisRun = generatorSummary.generated() - initial.totalEntries();
        var parsedThisRun = parserSummary.processed() - initial.parserEntries();
        var observedInputs = initial.inputEntries() + generatedThisRun - parsedThisRun;
        var awaitingParser = Math.max(0L, Math.min(corpusEntries, observedInputs));
        var parserPassesThisRun = parserSummary.passed() - initial.parserPassEntries();
        var tlcProcessedThisRun = tlcSummary.processed() - initial.tlcEntries();
        var observedTlcInputs =
                initial.tlcInputEntries() + parserPassesThisRun - tlcProcessedThisRun;
        var awaitingTlc = Math.max(0L, Math.min(corpusEntries, observedTlcInputs));
        var observedApalacheInputs = initial.apalacheInputEntries()
                + parserPassesThisRun
                - (apalacheSummary.processed() - initial.apalacheEntries());
        var awaitingApalache = Math.max(
                0L, Math.min(corpusEntries, observedApalacheInputs));
        return new WorkflowProgress(
                phase,
                generatorSummary,
                parserSummary,
                tlcSummary,
                apalacheSummary,
                corpusEntries,
                awaitingParser,
                awaitingTlc,
                awaitingApalache,
                metrics.totalElapsed(invocationElapsed.elapsed()));
    }

    private static StageVerdictSummary parserSummary(
            CorpusInventory inventory, Duration elapsed) {
        return new StageVerdictSummary(
                inventory.parserPassEntries(),
                inventory.parserFailEntries(),
                inventory.parserCrashEntries(),
                elapsed);
    }

    private static StageVerdictSummary tlcSummary(
            CorpusInventory inventory, Duration elapsed) {
        return new StageVerdictSummary(
                inventory.tlcPassEntries(),
                inventory.tlcFailEntries(),
                inventory.tlcCrashEntries(),
                elapsed);
    }

    private static StageVerdictSummary apalacheSummary(
            CorpusInventory inventory, Duration elapsed) {
        return new StageVerdictSummary(
                inventory.apalachePassEntries(),
                inventory.apalacheFailEntries(),
                inventory.apalacheCrashEntries(),
                elapsed);
    }

    private void validateOccupancy(CorpusInventory inventory) throws WorkflowException {
        if (inventory.totalEntries() > config.workflow().maximumEntries()) {
            throw new WorkflowException(
                    "corpus contains more entries than workflow.max_entries");
        }
        if (inventory.inputEntries() > config.workflow().inputs().maximumEntries()) {
            throw new WorkflowException(
                    "00-inputs exceeds workflow.inputs.max_entries");
        }
        if (inventory.parserResultEntries()
                > config.workflow().parser().maximumEntries()) {
            throw new WorkflowException(
                    "parser result directories exceed workflow.parser.max_entries");
        }
        if (inventory.tlcEntries() > config.workflow().tlc().maximumEntries()) {
            throw new WorkflowException(
                    "TLC result directories exceed workflow.tlc.max_entries");
        }
        if (inventory.apalacheEntries()
                > config.workflow().apalache().maximumEntries()) {
            throw new WorkflowException(
                    "Apalache result directories exceed workflow.apalache.max_entries");
        }
    }

    private record StageRunResult(
            WorkflowRunSummary.StopReason stopReason,
            PbtStageSummary generator,
            StageVerdictSummary parser,
            StageVerdictSummary tlc,
            StageVerdictSummary apalache,
            CorpusInventory corpus) {
        WorkflowRunSummary summary(Duration totalElapsed) {
            return new WorkflowRunSummary(
                    stopReason, generator, parser, tlc, apalache, corpus, totalElapsed);
        }
    }

    /** Stops the invocation clock and saves its aggregate while the corpus lock is still held. */
    private static final class StatisticsOnExit implements AutoCloseable {
        private final CorpusDirectory corpus;
        private final WorkflowMetrics metrics;
        private final ElapsedTimeAccumulator invocationElapsed;

        private Duration totalElapsed;

        private StatisticsOnExit(
                CorpusDirectory corpus,
                WorkflowMetrics metrics,
                ElapsedTimeAccumulator invocationElapsed) {
            this.corpus = corpus;
            this.metrics = metrics;
            this.invocationElapsed = invocationElapsed;
        }

        @Override
        public void close() throws IOException {
            var interrupted = Thread.interrupted();
            try {
                invocationElapsed.stop();
                var currentInvocation = invocationElapsed.elapsed();
                totalElapsed = metrics.totalElapsed(currentInvocation);
                corpus.writeRunStatistics(metrics.snapshot(currentInvocation));
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private Duration totalElapsed() {
            if (totalElapsed == null) {
                throw new IllegalStateException("workflow statistics have not been saved");
            }
            return totalElapsed;
        }
    }
}
