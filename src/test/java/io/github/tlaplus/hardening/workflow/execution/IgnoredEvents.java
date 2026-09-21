package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.EntryName;

/**
 * A {@link WorkflowEvents} that tracks nothing, for stages exercised outside generation scheduling.
 * Every entry is untracked, so queued work keeps the order it was submitted in.
 */
public final class IgnoredEvents implements WorkflowEvents {
    public static final WorkflowEvents INSTANCE = new IgnoredEvents();

    private IgnoredEvents() {}

    @Override
    public void admitted(EntryName entry, int generation, boolean mutant) {}

    @Override
    public void completed(EntryName entry, CorpusStage stage, CorpusVerdict verdict) {}

    @Override
    public int generationOf(EntryName entry) {
        return UNKNOWN_GENERATION;
    }
}
