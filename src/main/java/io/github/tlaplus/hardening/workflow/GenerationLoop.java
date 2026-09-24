package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.EntryOrigin;
import io.github.tlaplus.hardening.mutation.ByteMutator;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.input.CandidateSource;
import io.github.tlaplus.hardening.workflow.input.GenerationPlan;
import io.github.tlaplus.hardening.workflow.input.InputStage;
import io.github.tlaplus.hardening.workflow.input.MetamorphicCandidates;
import io.github.tlaplus.hardening.workflow.input.MutantCandidates;
import io.github.tlaplus.hardening.workflow.input.ParentPool;
import io.github.tlaplus.hardening.workflow.input.PbtCandidates;
import io.github.tlaplus.hardening.workflow.quality.QualityGate;
import io.github.tlaplus.hardening.workflow.spec.EvaluatedExprs;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the generations of one workflow invocation over a single stage graph (ADR 0010).
 *
 * <p>One {@link StageGraph} lives for the whole invocation, and this loop decides what its input
 * stage admits and when the quality gate runs. For each generation g it
 *
 * <ol>
 *   <li>admits what g still lacks, one {@link EntryOrigin} range after the other — mutants of the
 *       parents {@code 04quality-pass} holds, lifted entries, then PBT — and waits until every one
 *       of those entries is stored;
 *   <li>admits the PBT range of g + 1 while g's checker tail is still running, which is what keeps
 *       the CPUs busy across a generation boundary;
 *   <li>waits until every entry of g has settled, runs the gate over g, and moves on.
 * </ol>
 *
 * <p>Only g + 1's <em>PBT</em> share may run ahead: its mutants depend on g's selection, so they
 * wait for the gate. At most one generation is therefore open ahead of the oldest ungated one, and
 * queued work and CPU-budget requests from the older generation are served first. The loop ends
 * when the corpus has run every generation {@code workflow.max_entries} allows, or when a stage
 * stops the run.
 *
 * <p>A run resumes at the oldest generation that is still short of entries, still has work in
 * flight, or is still ungated; see {@link #oldestIncomplete}. Admitting nothing, draining nothing
 * and gating nothing is a no-op, so resuming a finished generation simply moves on.
 *
 * <p><strong>Threading.</strong> {@link #run} owns the coordinator thread, and {@link #reservations}
 * and the {@link GenerationTargets} in it are confined to it. Only {@link #current} crosses
 * threads: the progress monitor reads it while the loop runs.
 */
final class GenerationLoop {
    /** The generation being admitted or gated now, and the graph that every generation shares. */
    record Generation(int number, StageGraph graph) {
        Generation {
            Objects.requireNonNull(graph, "graph");
        }
    }

    private final StageGraph.Setup setup;
    private final StageGraph.Invocation invocation;
    private final StageGraph graph;
    private final GenerationProgress progress;
    private final QualityGate gate;
    private final Map<Integer, GenerationTargets> reservations = new HashMap<>();
    private volatile Generation current;

    GenerationLoop(
            StageGraph.Setup setup,
            StageGraph.Invocation invocation,
            StageGraph.Startup startup,
            OccupancyLimits limits)
            throws IOException, CorpusException {
        this.setup = Objects.requireNonNull(setup, "setup");
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(startup, "startup");
        Objects.requireNonNull(limits, "limits");
        graph = new StageGraph(setup, invocation, startup);
        progress = graph.progress();
        gate = new QualityGate(
                invocation.corpus(),
                setup.config().mutator().gate(),
                new EvaluatedExprs(setup.decoders())::count,
                graph.counters(CorpusStage.QUALITY));
        current = new Generation(oldestIncomplete(startup.initial()), graph);
        if (limits.exhausted(startup.initial())) {
            graph.control().capacityReached();
        }
    }

    /** Returns the oldest generation that has not been gated: the one the run is working on. */
    Generation current() {
        return current;
    }

    /**
     * Runs generations until the corpus is full or a stage stops the run, and reports which. A
     * stage failure is thrown. The graph is always finished and closed, even on the failure path,
     * so no worker outlives this call.
     */
    WorkflowRunSummary.StopReason run() throws IOException, CorpusException, WorkflowException {
        var started = false;
        try {
            graph.start();
            started = true;
            var generation = current.number();
            while (!graph.control().shouldStop() && size(generation) > 0) {
                current = new Generation(generation, graph);
                admit(generation);
                if (!progress.awaitAdmitted(generation, size(generation))) {
                    break;
                }
                if (size(generation + 1) > 0) {
                    admitPbtAhead(generation + 1);
                }
                if (!progress.awaitSettled(generation)) {
                    break;
                }
                gate.run(generation);
                reservations.remove(generation);
                generation++;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            graph.control().fail(exception);
        } catch (IOException | CorpusException | WorkflowException | RuntimeException | Error exception) {
            graph.control().fail(exception);
            throw exception;
        } finally {
            try {
                if (started) {
                    graph.finish();
                }
            } finally {
                graph.close();
            }
        }
        graph.throwIfFailed();
        return graph.control().state() == WorkflowControl.State.CAPACITY_REACHED
                ? WorkflowRunSummary.StopReason.CAPACITY_REACHED
                : WorkflowRunSummary.StopReason.COMPLETED;
    }

    /**
     * Returns the oldest generation a restart may still need to complete or gate.
     *
     * <p>Only the generation before the latest one can have been left open: the loop admits at
     * most one generation ahead of the one it is gating. An older generation with work in flight or
     * entries still ungated would be skipped silently, so such a corpus is rejected. Being short of
     * {@code generation_size} is not evidence of that — raising {@code generation_size} between
     * runs makes every finished generation look short — so an older generation is judged only by
     * what it left open.
     */
    private int oldestIncomplete(CorpusInventory initial) throws CorpusException {
        var latest = initial.latestGeneration();
        for (var generation = 0; generation < latest - 1; generation++) {
            if (leftOpen(initial, generation)) {
                throw new CorpusException(
                        "generation " + generation + " has unfinished or ungated entries but"
                                + " generation " + latest + " has been admitted; this corpus was not"
                                + " written by this workflow");
            }
        }
        if (latest > 0
                && (initial.entries(latest - 1) < size(latest - 1) || leftOpen(initial, latest - 1))) {
            return latest - 1;
        }
        return latest;
    }

    /** Reports whether a generation has entries in flight or entries the gate has not judged. */
    private static boolean leftOpen(CorpusInventory initial, int generation) {
        return initial.unsettled(generation) > 0 || initial.ungated(generation) > 0;
    }

    /**
     * Returns how many entries a generation admits: a full {@code generation_size}, or what is left
     * of {@code workflow.max_entries} if this is the last one. The budget is counted nominally, from
     * the generation's number, so that a generation's target ordinals do not depend on how many
     * entries its predecessors actually stored.
     */
    private long size(int generation) {
        var remaining = setup.config().workflow().maximumEntries()
                - (long) generation * setup.config().mutator().generationSize();
        return Math.max(0, Math.min(setup.config().mutator().generationSize(), remaining));
    }

    /** Admits everything a generation still lacks, one origin's range after the other. */
    private void admit(int generation) throws IOException, CorpusException {
        var targets = targets(generation);
        for (var origin : EntryOrigin.values()) {
            var count = targets.remaining(origin);
            if (count > 0) {
                submit(generation, targets.reserve(origin, count), count, source(origin));
            }
        }
    }

    /** Returns the source that fills the range of {@code origin}. */
    private CandidateSource source(EntryOrigin origin) throws IOException, CorpusException {
        return switch (origin) {
            case MUTANT -> mutantSource();
            case LIFTED -> liftSource();
            case PBT -> new PbtCandidates(setup.config().pbt());
        };
    }

    /**
     * Admits the PBT suffix of the generation after the current one, so its entries parse and check
     * while the current generation's checker tail runs. Its mutant range cannot follow yet: the
     * parents it would mutate are whatever the pending quality gate selects.
     */
    private void admitPbtAhead(int generation) {
        var targets = targets(generation);
        var count = targets.remaining(EntryOrigin.PBT);
        if (count > 0) {
            submit(generation, targets.reserve(EntryOrigin.PBT, count), count, new PbtCandidates(setup.config().pbt()));
        }
    }

    /**
     * Returns the source that fills a generation's mutant range. Until an entry has passed the
     * quality gate there is nothing to mutate — generation 0 never reaches here, but a later
     * generation whose predecessors were all dropped can — and PBT fills the range instead.
     */
    private CandidateSource mutantSource() throws IOException, CorpusException {
        var kind = setup.config().generatedKind();
        var parents = ParentPool.load(invocation.corpus(), kind);
        if (parents.isEmpty()) {
            return new PbtCandidates(setup.config().pbt());
        }
        return new MutantCandidates(parents, setup.decoders().decoder(kind), new ByteMutator(
                setup.config().mutator().weights(),
                setup.config().mutator().maximumEdits(),
                setup.config().pbt().maximumInputBytes()));
    }

    /**
     * Returns the source that fills a generation's lifted range: the base corpus's {@code
     * 04quality-pass} entries, read once per generation and never written. PBT fills the range while
     * the base corpus has none.
     */
    private CandidateSource liftSource() throws IOException, CorpusException {
        var base = setup.lifting().baseCorpus().orElseThrow();
        var parents = ParentPool.load(CorpusDirectory.openExisting(base), setup.config().generatedKind());
        return parents.isEmpty() ? new PbtCandidates(setup.config().pbt()) : new MetamorphicCandidates(parents);
    }

    /** Hands the input stage one contiguous range of a generation's targets. */
    private void submit(int generation, long firstTarget, long count, CandidateSource source) {
        graph.admit(new GenerationPlan(
                setup.config().generatedKind(),
                generation,
                InputStage.generationSeed(invocation.seed(), generation),
                (long) generation * setup.config().mutator().generationSize(),
                invocation.maximumCpus(),
                firstTarget,
                List.of(new GenerationPlan.Quota(source, count))));
    }

    /** Returns a generation's reservation, seeded on first use from what the corpus already holds. */
    private GenerationTargets targets(int generation) {
        return reservations.computeIfAbsent(generation, number -> GenerationTargets.resuming(
                size(number),
                Map.of(EntryOrigin.MUTANT, GenerationTargets.mutantShare(
                                size(number), number, setup.config().mutator().feedbackRatio()),
                        EntryOrigin.LIFTED, setup.lifting().baseCorpus().isEmpty()
                                ? 0L : GenerationTargets.liftShare(size(number), setup.lifting().ratio())),
                progress.admittedByOrigin(number)));
    }
}
