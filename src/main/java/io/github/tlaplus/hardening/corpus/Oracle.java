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
    },
    /**
     * Passes when the checkers agree and none reports a counterexample (ADR 0016 §4). A
     * counterexample violates the metamorphic relation: an unsound rule or a checker defect, even
     * when every checker reports it. A failure on a partial term fails both sides alike and passes.
     */
    METAMORPHIC {
        @Override
        CorpusVerdict judge(Collection<CorpusVerdict> verdicts) {
            return verdicts.stream().distinct().count() == 1 && !verdicts.contains(CorpusVerdict.COUNTEREXAMPLE)
                    ? CorpusVerdict.PASS
                    : CorpusVerdict.FAIL;
        }
    };

    /** Judges the non-crash verdicts of every checker that ran on one entry. */
    abstract CorpusVerdict judge(Collection<CorpusVerdict> verdicts);
}
