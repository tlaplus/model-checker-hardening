package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.EntryName;

/**
 * Durable admission and stage transitions, reported to whatever schedules generations (ADR 0010).
 *
 * <p>Every method is called from a stage worker thread, and an implementation must tolerate
 * concurrent calls. A stage reports a transition <em>after</em> it has durably moved the entry, so
 * an observer that acts on the report always finds the entry in its new directory.
 *
 * <p>That is the only ordering guaranteed. One entry's reports from different stages may arrive
 * out of pipeline order: a moved entry is visible before its report, so a downstream stage can
 * act on it and report first. In the current pipeline the aggregator can overtake the slower
 * checker's report; the parser's report always precedes the checkers', because a stage reports
 * before it forwards.
 */
public interface WorkflowEvents {
    /**
     * The generation reported for an entry the implementation does not track. Ordering is total, so
     * an untracked entry must still compare: it sorts last, behind every known generation.
     */
    int UNKNOWN_GENERATION = Integer.MAX_VALUE;

    /** Reports that an entry was stored in the corpus and belongs to {@code generation}. */
    void admitted(EntryName entry, int generation, boolean mutant);

    /** Reports the verdict one stage durably recorded on an entry. */
    void completed(EntryName entry, CorpusStage stage, CorpusVerdict verdict);

    /**
     * Returns the generation an entry belongs to, for ordering work oldest generation first, or
     * {@link #UNKNOWN_GENERATION} for an entry this implementation does not track. This is a
     * comparison key used inside queue locks, so it never throws.
     */
    int generationOf(EntryName entry);
}
