package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowEvents;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Tracks only entries that have not reached a terminal stage outcome. */
final class GenerationProgress implements WorkflowEvents {
    private final Map<String, Entry> unsettled = new HashMap<>();
    private final Map<Integer, Counts> generations = new HashMap<>();
    private final WorkflowControl control;

    GenerationProgress(CorpusInventory initial, WorkflowControl control) {
        this.control = Objects.requireNonNull(control, "control");
        initial.generations().forEach((generation, admitted) -> {
            var counts = counts(generation);
            counts.admitted = admitted.entries();
            counts.mutants = admitted.mutants();
        });
        initial.unsettled().forEach((name, progress) -> {
            unsettled.put(name, new Entry(progress));
            counts(progress.generation()).unsettled++;
        });
        control.onStop(this::signal);
    }

    @Override
    public synchronized void admitted(Path path, int generation, boolean mutant) {
        var name = name(path);
        if (unsettled.putIfAbsent(name, new Entry(generation)) != null) {
            throw new IllegalStateException("entry admitted twice: " + path);
        }
        var counts = counts(generation);
        counts.admitted++;
        counts.unsettled++;
        if (mutant) {
            counts.mutants++;
        }
        notifyAll();
    }

    @Override
    public synchronized void completed(Path path, CorpusStage stage, CorpusVerdict verdict) {
        var entry = require(path);
        if (stage == CorpusStage.PARSER) {
            if (verdict != CorpusVerdict.PASS) {
                settle(path, entry);
            }
        } else if (CorpusStage.checkerBranches().contains(stage)) {
            entry.checkers.add(stage);
            entry.crashed |= verdict == CorpusVerdict.CRASH;
            if (entry.crashed && entry.checkers.size() == CorpusStage.checkerBranches().size()) {
                settle(path, entry);
            }
        } else if (stage == CorpusStage.AGGREGATOR) {
            settle(path, entry);
        } else {
            throw new IllegalArgumentException("not a pipeline stage: " + stage);
        }
    }

    @Override
    public synchronized int generationOf(Path path) {
        return require(path).generation;
    }

    synchronized long admitted(int generation) {
        return counts(generation).admitted;
    }

    synchronized long mutants(int generation) {
        return counts(generation).mutants;
    }

    synchronized long unsettled(int generation) {
        return counts(generation).unsettled;
    }

    synchronized boolean awaitAdmitted(int generation, long target) throws InterruptedException {
        while (admitted(generation) < target && !control.shouldStop()) {
            wait();
        }
        return !control.shouldStop();
    }

    synchronized boolean awaitSettled(int generation) throws InterruptedException {
        while (unsettled(generation) > 0 && !control.shouldStop()) {
            wait();
        }
        return !control.shouldStop();
    }

    private void settle(Path path, Entry entry) {
        unsettled.remove(name(path));
        counts(entry.generation).unsettled--;
        notifyAll();
    }

    private Entry require(Path path) {
        var entry = unsettled.get(name(path));
        if (entry == null) {
            throw new IllegalStateException("no unsettled entry for " + path);
        }
        return entry;
    }

    private Counts counts(int generation) {
        return generations.computeIfAbsent(generation, _ -> new Counts());
    }

    private static String name(Path path) {
        return path.getFileName().toString();
    }

    private synchronized void signal() {
        notifyAll();
    }

    private static final class Counts {
        long admitted;
        long mutants;
        long unsettled;
    }

    private static final class Entry {
        final int generation;
        final EnumSet<CorpusStage> checkers = EnumSet.noneOf(CorpusStage.class);
        boolean crashed;

        Entry(int generation) {
            this.generation = generation;
        }

        Entry(CorpusInventory.PendingGenerationEntry progress) {
            this(progress.generation());
            checkers.addAll(progress.completedCheckers());
            crashed = progress.crashed();
        }
    }
}
