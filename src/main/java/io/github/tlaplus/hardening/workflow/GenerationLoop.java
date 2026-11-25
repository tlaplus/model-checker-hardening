package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.EntryOrigin;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.mutation.ByteMutator;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.input.CandidateSource;
import io.github.tlaplus.hardening.workflow.input.GenerationPlan;
import io.github.tlaplus.hardening.workflow.input.InputStage;
import io.github.tlaplus.hardening.workflow.input.MetamorphicCandidates;
import io.github.tlaplus.hardening.workflow.input.MutantCandidates;
import io.github.tlaplus.hardening.workflow.input.ParentPool;
import io.github.tlaplus.hardening.workflow.input.PbtCandidates;
import io.github.tlaplus.hardening.workflow.input.ReplayCandidates;
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
 *       parents {@code 04quality-pass} holds, adopted entries, then PBT — and, with replay, the
 *       counterexamples recorded on g - 1, and waits until every one of those entries is stored or,
 *       for a replay, skipped (ADR 0034 §6);
 *   <li>admits the PBT range of g + 1 while g's checker tail is still running, which is what keeps
 *       the CPUs busy across a generation boundary;
 *   <li>waits until every entry of g has settled, runs the gate over g, and moves on.
 * </ol>
 *
 * <p>Only g + 1's <em>PBT</em> share may run ahead: its mutants depend on g's selection, so they
 * wait for the gate. At most one generation is therefore open ahead of the oldest ungated one, and
 * queued work and CPU-budget requests from the older generation are served first. The loop ends
 * when the corpus has run every generation {@code workflow.max_entries} allows, and the replays of
 * the last of them in one more, or when a stage stops the run.
 *
 * <p>A run resumes at the oldest generation that is still short of entries, still has work in
 * flight, or is still ungated; see {@link #oldestIncomplete}. Admitting nothing, draining nothing
 * and gating nothing is a no-op, so resuming a finished generation simply moves on.
 *
 * <p><strong>Threading.</strong> {@link #run} owns the coordinator thread, and {@link #reservations}
 * and the {@link GenerationTargets} in it are confined to it. Only {@link #current} crosses
 * threads: the progress monitor reads it while the loop runs.
 */
@ArchRole(Role.PROCESS)
@Key
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
    /** The replays each generation admits, collected once its predecessor is gated (ADR 0034 §6). */
    private final Map<Integer, List<ReplayCandidates>> replays = new HashMap<>();
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
                new EvaluatedExprs(setup.decoders())::analyze,
                graph.counters(CorpusStage.QUALITY),
                invocation.technique().ranking());
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
            while (!graph.control().shouldStop() && (size(generation) > 0 || replayTargets(generation) > 0)) {
                current = new Generation(generation, graph);
                var replayTargets = admit(generation);
                if (!progress.awaitAdmitted(generation, size(generation))
                        || !progress.awaitReplays(generation, replayTargets)) {
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
                replays.remove(generation);
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
        // Replays come on top of a generation's size, so they do not make up for missing entries.
        var sized = latest > 0 ? initial.entries(latest - 1) - initial.admitted(latest - 1, EntryOrigin.REPLAY) : 0;
        if (latest > 0 && (sized < size(latest - 1) || leftOpen(initial, latest - 1))) {
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

    /**
     * Admits everything a generation still lacks, one origin's range after the other, then the
     * replays of its predecessor's counterexamples, on top of its size (ADR 0034 §6). Returns how
     * many replay targets the generation has: those it already holds and those submitted now.
     */
    private long admit(int generation) throws IOException, CorpusException {
        var targets = targets(generation);
        for (var origin : EntryOrigin.SIZED) {
            var count = targets.remaining(origin);
            if (count > 0) {
                submit(generation, targets.reserve(origin, count), count, source(origin));
            }
        }
        var pending = replaysOf(generation);
        if (!pending.isEmpty()) {
            graph.admit(new GenerationPlan(
                    InputKind.TRACE,
                    generation,
                    InputStage.generationSeed(invocation.seed(), generation),
                    (long) generation * setup.config().mutator().generationSize(),
                    invocation.maximumCpus(),
                    // Above every ordinal of the generation's size; a replay draws no randomness.
                    size(generation),
                    pending.stream().map(source -> new GenerationPlan.Quota(source, 1)).toList()));
        }
        return replayTargets(generation);
    }

    /**
     * Returns how many replay targets a generation has: the replays it holds and those it still
     * lacks. A generation past {@code workflow.max_entries} runs while it has any, so a run
     * interrupted in its last, replay-only generation still gates it.
     */
    private long replayTargets(int generation) throws IOException, CorpusException {
        return progress.admittedByOrigin(generation).getOrDefault(EntryOrigin.REPLAY, 0L)
                + replaysOf(generation).size();
    }

    /**
     * Returns the replays a generation admits: the counterexamples recorded on its predecessor,
     * which has settled and been gated, that the corpus does not hold yet. Without replay, or for
     * generation 0, there are none.
     */
    private List<ReplayCandidates> replaysOf(int generation) throws IOException, CorpusException {
        if (!setup.config().workflow().replay() || generation == 0) {
            return List.of();
        }
        var collected = replays.get(generation);
        if (collected == null) {
            collected = ReplayCandidates.collect(invocation.corpus(), generation - 1);
            replays.put(generation, collected);
        }
        return collected;
    }

    /** Returns the source that fills the range of {@code origin}. */
    private CandidateSource source(EntryOrigin origin) throws IOException, CorpusException {
        return switch (origin) {
            case MUTANT -> mutantSource();
            case ADOPTED -> adoptSource();
            case PBT -> new PbtCandidates(setup.config().pbt());
            case REPLAY -> throw new IllegalArgumentException("replays come on top of a generation's ranges");
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
     * Returns the source that fills a generation's adopted range: the base corpus's {@code
     * 04quality-pass} entries, read once per generation and never written. PBT fills the range while
     * the base corpus has none.
     */
    private CandidateSource adoptSource() throws IOException, CorpusException {
        var base = setup.adoption().baseCorpus().orElseThrow();
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
                        EntryOrigin.ADOPTED, setup.adoption().baseCorpus().isEmpty()
                                ? 0L : GenerationTargets.adoptShare(size(number), setup.adoption().ratio())),
                progress.admittedByOrigin(number)));
    }
}
