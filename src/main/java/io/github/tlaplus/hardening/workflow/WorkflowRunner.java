package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.corpus.CheckingPolicy;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.signature.KnownDefectDatabaseException;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import io.github.tlaplus.hardening.workflow.execution.WorkflowProgressMonitor;
import io.github.tlaplus.hardening.workflow.spec.GraphOutcomes;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runs generation, parsing, TLC, Apalache, conformance aggregation and the quality gate under one
 * CPU budget.
 *
 * <p>This class owns one invocation's life cycle: it checks the limits, locks and recovers the
 * corpus, runs the {@link GenerationLoop}, reports progress, and saves the statistics on exit.
 */
@ArchRole(Role.FACADE)
@Key
public final class WorkflowRunner {
    private static final Duration PROGRESS_UPDATE_INTERVAL = Duration.ofSeconds(1);

    private final StageGraph.Setup setup;
    private final OccupancyLimits limits;
    private final Technique technique;
    private final CheckingPolicy checking;

    /** Runs the technique of {@code config}, {@code [workflow] technique} (ADR 0025 §1). */
    public WorkflowRunner(FuzzTlaConfig config) throws WorkflowException {
        this(config, SpecDecoders.prepare(Objects.requireNonNull(config, "config"), config.workflow().technique()));
    }

    /** Reads the configured known-defect databases before any corpus is locked. */
    WorkflowRunner(FuzzTlaConfig config, SpecDecoders decoders) throws WorkflowException {
        Objects.requireNonNull(config, "config");
        this.technique = config.workflow().technique();
        if (!technique.generates(config.generatedKind())) {
            throw new WorkflowException("this corpus runs " + technique.encodedName() + ", which does not generate "
                    + config.generatedKind().encodedName() + " inputs: set [generator] kind to one it generates");
        }
        checking = CheckingPolicy.of(technique, config.workflow().enabledCheckers(), new GraphOutcomes(decoders));
        final KnownDefectDatabase knownDefects;
        try {
            knownDefects = KnownDefectDatabase.load(config.workflow().inputs().knownDefects());
        } catch (KnownDefectDatabaseException exception) {
            throw new WorkflowException(
                    "invalid known-defect database: " + exception.getMessage(), exception);
        }
        // Only a metamorphic run adopts; another technique ignores [metamorphic].
        var adoption = technique == Technique.MT
                ? config.metamorphic().adoption()
                : new MetamorphicConfig.Adoption(Optional.empty(), 0.0);
        setup = new StageGraph.Setup(config, decoders, knownDefects, checking.checkers(), adoption);
        limits = new OccupancyLimits(config.workflow(), checking.checkers());
    }

    /** Runs the workflow with the generator seed of {@code [seeds]}; see {@link RunMode}. */
    public WorkflowRunSummary run(CorpusDirectory corpus, RunMode mode, int maximumCpus)
            throws IOException, CorpusException, WorkflowException {
        return runInternal(corpus, mode, maximumCpus, null);
    }

    /**
     * Runs the workflow while reporting progress immediately, once per second, and after workers
     * stop. Listener calls never overlap; a listener exception disables further reporting without
     * stopping the workflow.
     */
    public WorkflowRunSummary run(
            CorpusDirectory corpus,
            RunMode mode,
            int maximumCpus,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        return runInternal(
                corpus,
                mode,
                maximumCpus,
                Objects.requireNonNull(progressListener, "progressListener"));
    }

    private WorkflowRunSummary runInternal(
            CorpusDirectory corpus,
            RunMode mode,
            int maximumCpus,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        var invocationElapsed = new ElapsedTimeAccumulator();
        invocationElapsed.start();
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(mode, "mode");
        limits.requireCpus(maximumCpus);
        var invocation = new StageGraph.Invocation(corpus, technique, setup.config().seeds().generator(), maximumCpus,
                CorpusTools.apalache(setup.config()));
        requireAdoptableBase();

        try (var corpusLock = corpus.acquireExclusiveLock()) {
            requireRunnable(corpus, mode);
            var initial = corpus.recoverAndValidate(entryValidator(), checking);
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

    /**
     * Refuses a fresh-only run of a corpus that an earlier run has written, and marks a continued
     * one as not reproducible (ADR 0026). The caller holds the corpus lock.
     */
    private static void requireRunnable(CorpusDirectory corpus, RunMode mode)
            throws IOException, CorpusException, WorkflowException {
        if (corpus.isFresh()) {
            return;
        }
        if (mode == RunMode.FRESH) {
            throw new WorkflowException(
                    "the corpus has been run before; only a continued run may extend it, which marks it not reproducible");
        }
        corpus.markNotReproducible(Instant.now());
    }

    /** Requires the base corpus of an adoption to decode its entries as this run would. */
    private void requireAdoptableBase() throws IOException, CorpusException, WorkflowException {
        var base = setup.adoption().baseCorpus();
        if (base.isPresent()) {
            AdoptableBase.require(base.get(), setup.config().generator(), setup.decoders().libraryManifest(),
                    setup.decoders().guardManifest());
        }
    }

    private StageRunResult runStages(
            StageGraph.Invocation invocation,
            StageGraph.Startup startup,
            ElapsedTimeAccumulator invocationElapsed,
            Consumer<WorkflowProgress> progressListener)
            throws IOException, CorpusException, WorkflowException {
        var generations = new GenerationLoop(setup, invocation, startup, limits);
        var metrics = startup.metrics();
        var phase = new AtomicReference<>(WorkflowProgress.Phase.RUNNING);
        try (var progress = progressListener == null
                ? null
                : WorkflowProgressMonitor.start(
                        PROGRESS_UPDATE_INTERVAL,
                        () -> progressSnapshot(
                                phase.get(),
                                generations.current(),
                                invocation.seed(),
                                metrics,
                                invocationElapsed),
                        progressListener)) {
            var stopReason = generations.run();
            phase.set(WorkflowProgress.Phase.FINALIZING);
            var result = invocation.corpus().recoverAndValidate(entryValidator());
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
            GenerationLoop.Generation generation,
            long seed,
            WorkflowMetrics metrics,
            ElapsedTimeAccumulator invocationElapsed) {
        var generatorSummary = metrics.generator().summary(seed);
        var graph = generation.graph();
        var stages = graph.summaries();
        var corpusEntries = generatorSummary.generated();
        var backlog = graph.backlog();
        backlog.replaceAll((stage, pending) -> Math.min(pending, corpusEntries));
        return new WorkflowProgress(
                phase,
                generation.number(),
                generatorSummary,
                stages,
                backlog,
                corpusEntries,
                metrics.totalElapsed(invocationElapsed.elapsed()));
    }

    /**
     * Returns the policy that decides whether a stored input is still usable. Each entry selects
     * its own decoder; {@code generator.kind} only selects what the input stage adds to the corpus.
     * Every entry must record the generation that admitted it (ADR 0010).
     */
    private CorpusEntryValidator entryValidator() {
        return (entry, envelope) -> {
            if (envelope.generation().filter(metadata -> metadata.generation().isPresent()).isEmpty()) {
                throw new CorpusException("corpus entry records no generation: " + entry);
            }
            try {
                setup.decoders().decode(envelope.corpusInput());
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
