package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.EntryName;
import io.github.tlaplus.hardening.corpus.EntryProgress;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Where every generation of a run stands, tracked from stage events instead of a corpus rescan
 * (ADR 0010).
 *
 * <p>Only entries that have not settled are held, so the tracked set is bounded by what is in
 * flight rather than by the size of the corpus. {@link EntryProgress} decides what "settled" means,
 * and startup recovery reads the same rule from stored envelopes, so a resumed run and a fresh one
 * agree.
 *
 * <p><strong>Threading.</strong> Stage workers call {@link #admitted}, {@link #completed} and
 * {@link #generationOf}; the coordinator thread calls {@link #awaitAdmitted} and
 * {@link #awaitSettled}. Every method synchronizes on a private lock, so no caller can interfere
 * with the waits by locking the instance. {@link #generationOf} is called from inside a
 * {@link io.github.tlaplus.hardening.workflow.execution.WorkQueue} comparator and never throws.
 */
final class GenerationProgress implements WorkflowEvents {
    private final Object lock = new Object();
    private final Map<EntryName, EntryProgress> unsettled = new HashMap<>();
    private final Map<Integer, Counts> generations = new HashMap<>();
    private final WorkflowControl control;

    GenerationProgress(CorpusInventory initial, WorkflowControl control) {
        this.control = Objects.requireNonNull(control, "control");
        Objects.requireNonNull(initial, "initial");
        synchronized (lock) {
            initial.generations().forEach((generation, admitted) -> {
                var counts = counts(generation);
                counts.admitted = admitted.entries();
                counts.mutants = admitted.mutants();
            });
            initial.unsettled().forEach((name, progress) -> {
                unsettled.put(name, progress);
                counts(progress.generation()).unsettled++;
            });
        }
        control.onStop(this::signal);
    }

    @Override
    public void admitted(EntryName entry, int generation, boolean mutant) {
        Objects.requireNonNull(entry, "entry");
        synchronized (lock) {
            if (unsettled.putIfAbsent(entry, EntryProgress.admitted(generation)) != null) {
                throw new IllegalStateException("entry admitted twice: " + entry);
            }
            var counts = counts(generation);
            counts.admitted++;
            counts.unsettled++;
            if (mutant) {
                counts.mutants++;
            }
            lock.notifyAll();
        }
    }

    @Override
    public void completed(EntryName entry, CorpusStage stage, CorpusVerdict verdict) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(stage, "stage");
        if (stage.role() == CorpusStage.PipelineRole.SELECTION) {
            throw new IllegalArgumentException("not a pipeline stage: " + stage);
        }
        synchronized (lock) {
            var previous = unsettled.get(entry);
            if (previous == null) {
                throw new IllegalStateException("no unsettled entry for " + entry);
            }
            var updated = previous.with(stage, verdict);
            if (updated.isSettled()) {
                unsettled.remove(entry);
                counts(updated.generation()).unsettled--;
                lock.notifyAll();
            } else {
                unsettled.put(entry, updated);
            }
        }
    }

    @Override
    public int generationOf(EntryName entry) {
        Objects.requireNonNull(entry, "entry");
        synchronized (lock) {
            var progress = unsettled.get(entry);
            return progress == null ? UNKNOWN_GENERATION : progress.generation();
        }
    }

    /** Returns how many entries one generation has admitted, over the whole corpus. */
    long admitted(int generation) {
        synchronized (lock) {
            return counts(generation).admitted;
        }
    }

    /** Returns how many of one generation's admitted entries are mutants. */
    long mutants(int generation) {
        synchronized (lock) {
            return counts(generation).mutants;
        }
    }

    /** Returns how many of one generation's entries no stage has finished with. */
    long unsettled(int generation) {
        synchronized (lock) {
            return counts(generation).unsettled;
        }
    }

    /**
     * Waits until one generation has admitted {@code target} entries. Returns {@code false} when the
     * run stopped first, in which case the target may never be reached.
     */
    boolean awaitAdmitted(int generation, long target) throws InterruptedException {
        synchronized (lock) {
            while (counts(generation).admitted < target && !control.shouldStop()) {
                lock.wait();
            }
            return !control.shouldStop();
        }
    }

    /**
     * Waits until every entry of one generation has settled, so the quality gate may select from it.
     * Returns {@code false} when the run stopped first.
     */
    boolean awaitSettled(int generation) throws InterruptedException {
        synchronized (lock) {
            while (counts(generation).unsettled > 0 && !control.shouldStop()) {
                lock.wait();
            }
            return !control.shouldStop();
        }
    }

    private Counts counts(int generation) {
        return generations.computeIfAbsent(generation, _ -> new Counts());
    }

    private void signal() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private static final class Counts {
        long admitted;
        long mutants;
        long unsettled;
    }
}
