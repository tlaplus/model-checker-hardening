package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.mutation.ByteMutator;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.input.CandidateSource;
import io.github.tlaplus.hardening.workflow.input.GenerationPlan;
import io.github.tlaplus.hardening.workflow.input.InputStage;
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

/** Coordinates one-generation PBT lookahead over an invocation-long stage graph. */
final class GenerationLoop {
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
    private final Map<Integer, Reservation> reservations = new HashMap<>();
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

    Generation current() {
        return current;
    }

    WorkflowRunSummary.StopReason run() throws IOException, CorpusException, WorkflowException {
        var started = false;
        try {
            graph.start();
            started = true;
            var generation = current.number();
            while (!graph.control().shouldStop() && size(generation) > 0) {
                current = new Generation(generation, graph);
                admitCurrent(generation);
                if (!progress.awaitAdmitted(generation, size(generation))) {
                    break;
                }
                if (size(generation + 1) > 0) {
                    admitPbt(generation + 1);
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

    /** The oldest generation a restart may need to complete or gate. */
    private int oldestIncomplete(CorpusInventory initial) {
        var latest = initial.latestGeneration();
        if (latest > 0) {
            var previous = latest - 1;
            if (initial.entries(previous) < size(previous)
                    || initial.unsettled().values().stream().anyMatch(entry -> entry.generation() == previous)
                    || initial.ungated().getOrDefault(previous, 0L) > 0) {
                return previous;
            }
        }
        return latest;
    }

    private long size(int generation) {
        var remaining = setup.config().workflow().maximumEntries()
                - (long) generation * setup.config().mutator().generationSize();
        return Math.max(0, Math.min(setup.config().mutator().generationSize(), remaining));
    }

    private long mutantTarget(int generation) {
        return mutantTarget(size(generation), generation, setup.config().mutator().feedbackRatio());
    }

    private void admitCurrent(int generation) throws IOException, CorpusException {
        var reserved = reserved(generation);
        var missing = size(generation) - reserved.total();
        if (missing <= 0) {
            return;
        }
        var mutantTarget = mutantTarget(generation);
        var prefixMissing = Math.min(
                missing, Math.max(0, mutantTarget - reserved.mutants - reserved.pbtPrefix));
        if (prefixMissing > 0) {
            var kind = setup.config().generatedKind();
            var parents = ParentPool.load(invocation.corpus(), kind, setup.decoders().decoder(kind));
            if (!parents.isEmpty()) {
                submit(generation, reserved.mutants, prefixMissing,
                        new MutantCandidates(parents, new ByteMutator(
                                setup.config().mutator().weights(),
                                setup.config().mutator().maximumEdits(),
                                setup.config().pbt().maximumInputBytes())));
                reserved.mutants += prefixMissing;
            } else {
                submit(generation, reserved.pbtPrefix, prefixMissing,
                        new PbtCandidates(setup.config().pbt()));
                reserved.pbtPrefix += prefixMissing;
            }
            missing -= prefixMissing;
        }
        if (missing > 0) {
            submit(generation, mutantTarget + reserved.pbtSuffix, missing,
                    new PbtCandidates(setup.config().pbt()));
            reserved.pbtSuffix += missing;
        }
    }

    /** Admits only the PBT suffix; the mutant prefix waits for the preceding quality gate. */
    private void admitPbt(int generation) {
        var mutantTarget = mutantTarget(generation);
        var reserved = reserved(generation);
        var missing = Math.min(
                size(generation) - reserved.total(),
                Math.max(0, size(generation) - mutantTarget - reserved.pbtSuffix));
        if (missing > 0) {
            submit(generation, mutantTarget + reserved.pbtSuffix, missing,
                    new PbtCandidates(setup.config().pbt()));
            reserved.pbtSuffix += missing;
        }
    }

    private void submit(int generation, long firstTarget, long count, CandidateSource source) {
        if (count <= 0) {
            return;
        }
        graph.admit(new GenerationPlan(
                setup.config().generatedKind(),
                generation,
                InputStage.generationSeed(invocation.seed(), generation),
                (long) generation * setup.config().mutator().generationSize(),
                invocation.maximumCpus(),
                firstTarget,
                List.of(new GenerationPlan.Quota(source, count))));
    }

    private Reservation reserved(int generation) {
        return reservations.computeIfAbsent(generation, number -> {
            var pbt = progress.admitted(number) - progress.mutants(number);
            var suffix = Math.min(pbt, size(number) - mutantTarget(number));
            return new Reservation(progress.mutants(number), pbt - suffix, suffix);
        });
    }

    private static final class Reservation {
        long mutants;
        long pbtPrefix;
        long pbtSuffix;

        Reservation(long mutants, long pbtPrefix, long pbtSuffix) {
            this.mutants = mutants;
            this.pbtPrefix = pbtPrefix;
            this.pbtSuffix = pbtSuffix;
        }

        long total() {
            return mutants + pbtPrefix + pbtSuffix;
        }
    }

    static long mutantTarget(long size, int generation, double feedbackRatio) {
        return generation == 0 ? 0 : Math.round(feedbackRatio * size);
    }
}
