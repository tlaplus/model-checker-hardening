package io.github.tlaplus.hardening.corpus;

import java.util.Collection;

/**
 * How the aggregator judges the checker verdicts of one entry (fuzzing-workflows.md §1.3, ADR
 * 0016 §4). Each technique names one oracle.
 */
public enum Oracle {
    /**
     * Passes when all checker verdicts agree, and fails when they disagree. A counterexample
     * therefore passes only when every checker reports one.
     */
    CONFORMANCE {
        @Override
        CorpusVerdict judge(Collection<CorpusVerdict> verdicts) {
            return verdicts.stream().distinct().count() == 1 ? CorpusVerdict.PASS : CorpusVerdict.FAIL;
        }
    };

    /** Judges the non-crash verdicts of every checker that ran on one entry. */
    abstract CorpusVerdict judge(Collection<CorpusVerdict> verdicts);
}
