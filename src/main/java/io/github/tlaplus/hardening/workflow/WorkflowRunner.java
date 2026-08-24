package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.StageScratchSet;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage;
import io.github.tlaplus.hardening.workflow.checker.CheckerBackend;
import io.github.tlaplus.hardening.workflow.checker.CheckerStage;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import io.github.tlaplus.hardening.workflow.execution.WorkflowProgressMonitor;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.input.PbtStage;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.parser.ParserStage;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Runs generation, parsing, TLC, Apalache, and conformance aggregation under one CPU budget. */
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
        for (var checker : CorpusStage.checkerBranches()) {
            if (config.workflow().checker(checker).workers() > maximumCpus) {
                throw new WorkflowException(
                        "workflow."
                                + checker.metadataName()
                                + ".workers must not exceed run --max-cpus");
            }
        }
        var apalacheJar = ApalacheDistribution.locate();

        try (var corpusLock = corpus.acquireExclusiveLock()) {
            var initial = corpus.recoverAndValidate(entryValidator());
            validateOccupancy(initial);
            var metrics = new WorkflowMetrics(corpus.readRunStatistics(), initial.totalEntries());
            var statistics = new StatisticsOnExit(corpus, metrics, invocationElapsed);
            StageRunResult result;
            try (statistics) {
                try (var scratch = corpus.createScratch()) {
                    result = runStages(
                            corpus,
                            seed,
                            maximumCpus,
                            scratch,
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
            StageScratchSet scratch,
            Path apalacheJar,
            CorpusInventory initial,
            WorkflowMetrics metrics,
            ElapsedTimeAccumulator invocationElapsed,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        var queues = new EnumMap<CorpusStage, WorkQueue<Path>>(CorpusStage.class);
        var counters = new EnumMap<CorpusStage, StageCounters>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            var queue = new WorkQueue<Path>();
            initial.pending(stage).forEach(queue::submit);
            queues.put(stage, queue);
            counters.put(
                    stage,
                    new StageCounters(summary(initial, stage, metrics), metrics.clocks().of(stage)));
        }
        var control = new WorkflowControl(queues.values().toArray(WorkQueue<?>[]::new));
        var inputCapacity = new Semaphore(
                config.workflow().inputs().maximumEntries()
                        - Math.toIntExact(initial.pendingEntries(CorpusStage.PARSER)),
                true);
        var cpuBudget = new CpuBudget(maximumCpus);
        var environment = new StageEnvironment(corpus, generator, cpuBudget, control);

        var checkerCapacities = new EnumMap<CorpusStage, OccupancyGate>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            checkerCapacities.put(
                    checker,
                    new OccupancyGate(
                            initial.resultEntries(checker),
                            config.workflow().maximumEntries(checker)));
        }

        var aggregator = new AggregatorStage(
                initial.pendingEntries(CorpusStage.AGGREGATOR),
                counters.get(CorpusStage.AGGREGATOR),
                environment,
                queues.get(CorpusStage.AGGREGATOR),
                checkerCapacities);

        var parser = new ParserStage(
                config.workflow().parser(),
                maximumCpus,
                initial.resultEntries(CorpusStage.PARSER),
                counters.get(CorpusStage.PARSER),
                environment,
                scratch.directory(CorpusStage.PARSER),
                queues.get(CorpusStage.PARSER),
                checkerQueues(queues),
                inputCapacity);
        var checkers = new EnumMap<CorpusStage, CheckerStage>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            checkers.put(
                    checker,
                    new CheckerStage(
                            checkerBackend(checker, maximumCpus, scratch, apalacheJar),
                            checkerCapacities.get(checker),
                            counters.get(checker),
                            environment,
                            queues.get(checker),
                            queues.get(CorpusStage.AGGREGATOR)));
        }
        var pbt = new PbtStage(
                config.pbt(),
                config.workflow().maximumEntries(),
                initial.totalEntries(),
                environment,
                seed,
                maximumCpus,
                queues.get(CorpusStage.PARSER),
                inputCapacity,
                metrics.generator());
        var stages = new ArrayList<WorkflowStage>();
        stages.add(pbt);
        stages.add(parser);
        stages.addAll(checkers.values());
        stages.add(aggregator);
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
                                queues,
                                counters,
                                seed,
                                metrics,
                                invocationElapsed),
                        progressListener)) {
            try {
                // Drain recovered fan-in before checkers inspect their current result capacity.
                aggregator.start();
                aggregator.awaitRecovered();
                throwIfFailed(control);
                // Downstream stages start first so they can drain a recovered backlog immediately.
                for (var stage : checkers.values()) {
                    stage.start();
                }
                parser.start();
                pbt.start();
                pbt.await();
                parser.await();
                for (var stage : checkers.values()) {
                    stage.await();
                }
                queues.get(CorpusStage.AGGREGATOR).close();
                aggregator.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                control.fail(exception);
            } finally {
                stages.forEach(WorkflowStage::close);
            }

            throwIfFailed(control);
            progressPhase.set(WorkflowProgress.Phase.FINALIZING);
            var result = corpus.recoverAndValidate(entryValidator());
            var stopReason = control.state() == WorkflowControl.State.CAPACITY_REACHED
                    ? WorkflowRunSummary.StopReason.CAPACITY_REACHED
                    : WorkflowRunSummary.StopReason.COMPLETED;
            var finalSummaries = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
            for (var stage : CorpusStage.values()) {
                finalSummaries.put(stage, summary(result, stage, metrics));
            }
            return new StageRunResult(
                    stopReason, metrics.generator().summary(seed), finalSummaries, result);
        }
    }

    /** Returns the queue each checker branch takes its work from. */
    private static Map<CorpusStage, WorkQueue<Path>> checkerQueues(
            Map<CorpusStage, WorkQueue<Path>> queues) {
        var result = new EnumMap<CorpusStage, WorkQueue<Path>>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            result.put(checker, queues.get(checker));
        }
        return result;
    }

    /** Returns the checker backend of one stage, configured for this invocation. */
    private CheckerBackend checkerBackend(
            CorpusStage stage, int maximumCpus, StageScratchSet scratch, Path apalacheJar) {
        var checkerConfig = config.workflow().checker(stage);
        return switch (stage) {
            case TLC -> new TlcCheckerBackend(
                    checkerConfig,
                    maximumCpus / checkerConfig.workers(),
                    scratch.directory(CorpusStage.TLC));
            case APALACHE -> new ApalacheCheckerBackend(
                    checkerConfig, apalacheJar, scratch.directory(CorpusStage.APALACHE));
            default -> throw new AssertionError("unreachable stage: " + stage);
        };
    }

    /** Returns what one stage has produced according to an inventory of the corpus. */
    private static StageVerdictSummary summary(
            CorpusInventory inventory, CorpusStage stage, WorkflowMetrics metrics) {
        var counts = inventory.counts(stage);
        return new StageVerdictSummary(
                counts.passed(),
                counts.failed(),
                counts.crashed(),
                metrics.clocks().of(stage).elapsed());
    }

    private static WorkflowProgress progressSnapshot(
            WorkflowProgress.Phase phase,
            Map<CorpusStage, WorkQueue<Path>> queues,
            Map<CorpusStage, StageCounters> counters,
            long seed,
            WorkflowMetrics metrics,
            ElapsedTimeAccumulator invocationElapsed) {
        var generatorSummary = metrics.generator().summary(seed);
        var stages = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
        var backlog = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            stages.put(stage, counters.get(stage).summary());
            backlog.put(stage, (long) queues.get(stage).size());
        }
        var corpusEntries = generatorSummary.generated();
        for (var stage : CorpusStage.values()) {
            backlog.merge(stage, corpusEntries, Math::min);
        }
        return new WorkflowProgress(
                phase,
                generatorSummary,
                stages,
                backlog,
                corpusEntries,
                metrics.totalElapsed(invocationElapsed.elapsed()));
    }

    /**
     * Returns the policy that decides whether a stored input is still usable: this workflow runs
     * expression inputs, and the payload must decode to an expression under this run's generator
     * configuration.
     */
    private CorpusEntryValidator entryValidator() {
        return (entry, input) -> {
            if (input.kind() != CorpusInput.Kind.EXPRESSION) {
                throw new CorpusException(
                        "corpus entry is rejected: "
                                + entry
                                + ": this workflow runs '"
                                + CorpusInput.Kind.EXPRESSION.encodedName()
                                + "' inputs, but the entry holds '"
                                + input.kind().encodedName()
                                + "'");
            }
            try {
                generator.generate(input.input());
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

    /**
     * Reports whether the run can make no progress at all: a checker still has queued inputs it has
     * no capacity for, or the corpus is below its target but no input slot is available.
     */
    private boolean capacityIsAlreadyExhausted(CorpusInventory initial) {
        var aggregationCanReleaseCapacity = initial.pendingEntries(CorpusStage.AGGREGATOR) > 0;
        for (var checker : CorpusStage.checkerBranches()) {
            if (initial.resultEntries(checker) >= config.workflow().maximumEntries(checker)
                    && initial.pendingEntries(checker) > 0
                    && !aggregationCanReleaseCapacity) {
                return true;
            }
        }
        return config.workflow().inputs().maximumEntries() == 0
                && initial.totalEntries() < config.workflow().maximumEntries();
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

    /** Rejects a corpus that already exceeds any configured limit. */
    private void validateOccupancy(CorpusInventory inventory) throws WorkflowException {
        if (inventory.totalEntries() > config.workflow().maximumEntries()) {
            throw new WorkflowException(
                    "corpus contains more entries than workflow.max_entries");
        }
        if (inventory.pendingEntries(CorpusStage.PARSER)
                > config.workflow().inputs().maximumEntries()) {
            throw new WorkflowException("00-inputs exceeds workflow.inputs.max_entries");
        }
        for (var stage : CorpusStage.capacityLimitedStages()) {
            if (inventory.resultEntries(stage) > config.workflow().maximumEntries(stage)) {
                throw new WorkflowException(
                        stage.displayName()
                                + " result directories exceed workflow."
                                + stage.metadataName()
                                + ".max_entries");
            }
        }
    }

    private record StageRunResult(
            WorkflowRunSummary.StopReason stopReason,
            GeneratorSummary generator,
            Map<CorpusStage, StageVerdictSummary> stages,
            CorpusInventory corpus) {
        WorkflowRunSummary summary(Duration totalElapsed) {
            return new WorkflowRunSummary(stopReason, generator, stages, corpus, totalElapsed);
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
