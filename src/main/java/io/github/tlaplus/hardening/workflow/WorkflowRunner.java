package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.signature.KnownDefectDatabaseException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import io.github.tlaplus.hardening.workflow.execution.WorkflowProgressMonitor;
import io.github.tlaplus.hardening.workflow.library.LibraryManifest;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runs generation, parsing, TLC, Apalache, and conformance aggregation under one CPU budget.
 *
 * <p>This class owns one invocation's life cycle: it checks the limits, locks and recovers the
 * corpus, runs the {@link StageGraph}, reports progress, and saves the statistics on exit.
 */
public final class WorkflowRunner {
    private static final Duration PROGRESS_UPDATE_INTERVAL = Duration.ofSeconds(1);

    private final StageGraph.Setup setup;
    private final OccupancyLimits limits;

    public WorkflowRunner(FuzzTlaConfig config) throws WorkflowException {
        this(config, SpecDecoders.prepare(Objects.requireNonNull(config, "config")));
    }

    /** Reads the configured known-defect databases before any corpus is locked. */
    WorkflowRunner(FuzzTlaConfig config, SpecDecoders decoders) throws WorkflowException {
        Objects.requireNonNull(config, "config");
        final KnownDefectDatabase knownDefects;
        try {
            knownDefects = KnownDefectDatabase.load(config.workflow().inputs().knownDefects());
        } catch (KnownDefectDatabaseException exception) {
            throw new WorkflowException(
                    "invalid known-defect database: " + exception.getMessage(), exception);
        }
        setup = new StageGraph.Setup(config, decoders, knownDefects);
        limits = new OccupancyLimits(config.workflow());
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
        Preconditions.requireNonnegative(seed, "seed");
        limits.requireCpus(maximumCpus);
        var invocation = new StageGraph.Invocation(
                corpus, seed, maximumCpus, ApalacheDistribution.locate());

        try (var corpusLock = corpus.acquireExclusiveLock()) {
            LibraryManifest.verify(corpus, setup.decoders().libraryManifest(), true);
            var initial = corpus.recoverAndValidate(entryValidator());
            limits.requireWithin(initial);
            var metrics = new WorkflowMetrics(corpus.readRunStatistics(), initial.totalEntries());
            var statistics = new RunStatisticsOnExit(corpus, metrics, invocationElapsed);
            StageRunResult result;
            try (statistics; var scratch = corpus.createScratch()) {
                result = runStages(
                        invocation,
                        new StageGraph.Startup(initial, metrics, scratch),
                        invocationElapsed,
                        progressListener);
            }
            return result.summary(statistics.totalElapsed());
        }
    }

    private StageRunResult runStages(
            StageGraph.Invocation invocation,
            StageGraph.Startup startup,
            ElapsedTimeAccumulator invocationElapsed,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        var graph = new StageGraph(setup, invocation, startup);
        if (limits.exhausted(startup.initial())) {
            graph.control().capacityReached();
        }
        var metrics = startup.metrics();
        var phase = new AtomicReference<>(WorkflowProgress.Phase.RUNNING);
        try (var progress = progressListener == null
                ? null
                : WorkflowProgressMonitor.start(
                        PROGRESS_UPDATE_INTERVAL,
                        () -> progressSnapshot(
                                phase.get(), graph, invocation.seed(), metrics, invocationElapsed),
                        progressListener)) {
            graph.run();
            graph.throwIfFailed();
            phase.set(WorkflowProgress.Phase.FINALIZING);
            var result = invocation.corpus().recoverAndValidate(entryValidator());
            var stopReason = graph.control().state() == WorkflowControl.State.CAPACITY_REACHED
                    ? WorkflowRunSummary.StopReason.CAPACITY_REACHED
                    : WorkflowRunSummary.StopReason.COMPLETED;
            var finalSummaries = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
            for (var stage : CorpusStage.values()) {
                finalSummaries.put(stage, StageGraph.summary(result, stage, metrics));
            }
            return new StageRunResult(
                    stopReason, metrics.generator().summary(invocation.seed()), finalSummaries, result);
        }
    }

    private static WorkflowProgress progressSnapshot(
            WorkflowProgress.Phase phase,
            StageGraph graph,
            long seed,
            WorkflowMetrics metrics,
            ElapsedTimeAccumulator invocationElapsed) {
        var generatorSummary = metrics.generator().summary(seed);
        var stages = graph.summaries();
        var corpusEntries = generatorSummary.generated();
        var backlog = graph.backlog();
        backlog.replaceAll((stage, pending) -> Math.min(pending, corpusEntries));
        return new WorkflowProgress(
                phase,
                generatorSummary,
                stages,
                backlog,
                corpusEntries,
                metrics.totalElapsed(invocationElapsed.elapsed()));
    }

    /**
     * Returns the policy that decides whether a stored input is still usable. Each entry selects
     * its own decoder; {@code generator.kind} only selects what the input stage adds to the corpus.
     */
    private CorpusEntryValidator entryValidator() {
        return (entry, input) -> {
            try {
                setup.decoders().decode(input);
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

    private record StageRunResult(
            WorkflowRunSummary.StopReason stopReason,
            GeneratorSummary generator,
            Map<CorpusStage, StageVerdictSummary> stages,
            CorpusInventory corpus) {
        WorkflowRunSummary summary(Duration totalElapsed) {
            return new WorkflowRunSummary(stopReason, generator, stages, corpus, totalElapsed);
        }
    }
}
