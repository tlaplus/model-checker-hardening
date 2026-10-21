package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import java.util.Collection;
import java.util.Optional;

/**
 * How the aggregator judges the checker results of one entry (fuzzing-workflows.md §1.3, ADR 0016
 * §4, ADR 0019). Each technique names one oracle.
 *
 * <p>Both oracles require the checkers to agree: they report the same verdict, and every checker
 * whose search completed and that counts the distinct reachable states reports the same count.
 */
@ArchRole(Role.POLICY)
@Key
public enum Oracle {
    /**
     * Passes when the checkers agree, and fails when they disagree. A counterexample therefore
     * passes only when every checker reports one.
     */
    CONFORMANCE {
        @Override
        CorpusVerdict judge(Collection<StageRecord> results) {
            return agree(results) ? CorpusVerdict.PASS : CorpusVerdict.FAIL;
        }
    },
    /**
     * Passes when the checkers agree and none reports a counterexample (ADR 0016 §4). A
     * counterexample violates the metamorphic relation: an unsound rule or a checker defect, even
     * when every checker reports it. A failure on a partial term fails both sides alike and passes.
     */
    METAMORPHIC {
        @Override
        CorpusVerdict judge(Collection<StageRecord> results) {
            return agree(results)
                            && results.stream().noneMatch(result -> result.verdict() == CorpusVerdict.COUNTEREXAMPLE)
                    ? CorpusVerdict.PASS
                    : CorpusVerdict.FAIL;
        }
    };

    /** Judges the non-crash results of every checker that ran on one entry. */
    abstract CorpusVerdict judge(Collection<StageRecord> results);

    private static boolean agree(Collection<StageRecord> results) {
        return results.stream().map(StageRecord::verdict).distinct().count() == 1
                && results.stream()
                                .map(StageRecord::metrics)
                                .flatMap(metrics -> reachableStates(metrics).stream())
                                .distinct()
                                .count()
                        <= 1;
    }

    /**
     * Returns the size of the reachable state space, which only a completed search measures. A
     * checker stopped by a counterexample or an error has explored a prefix that depends on its
     * search order, and a bounded checker does not count distinct states (ADR 0019).
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Optional<Long> reachableStates(Optional<ExplorationMetrics> metrics) {
        return metrics.filter(value -> value.phase().equals(Optional.of(ExplorationPhase.COMPLETE)))
                .flatMap(value -> Optional.ofNullable(value.counts().get(ExplorationCount.DISTINCT_STATES)));
    }
}
