package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

/**
 * How the aggregator judges the checker results of one entry (fuzzing-workflows.md §1.3, ADR 0016
 * §4, ADR 0019, ADR 0023 §5). Each technique names one oracle.
 *
 * <p>Every oracle requires the checkers to agree: they report the same verdict, and every checker
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
        CorpusVerdict judge(
                Map<CorpusStage, StageRecord> results, Optional<ExpectedOutcome> expected, Set<CorpusStage> exitStatusOnly) {
            return agree(results, exitStatusOnly) ? CorpusVerdict.PASS : CorpusVerdict.FAIL;
        }
    },
    /**
     * Passes when the checkers agree and none reports a counterexample (ADR 0016 §4). A
     * counterexample violates the metamorphic relation: an unsound rule or a checker defect, even
     * when every checker reports it. A failure on a partial term fails both sides alike and passes.
     */
    METAMORPHIC {
        @Override
        CorpusVerdict judge(
                Map<CorpusStage, StageRecord> results, Optional<ExpectedOutcome> expected, Set<CorpusStage> exitStatusOnly) {
            return agree(results, exitStatusOnly)
                            && results.values().stream()
                                    .noneMatch(result -> result.verdict() == CorpusVerdict.COUNTEREXAMPLE)
                    ? CorpusVerdict.PASS
                    : CorpusVerdict.FAIL;
        }
    },
    /**
     * Passes when the checkers agree and every one reports the outcome the input predicts, or fails
     * (ADR 0023 §5). A checker that reports a pass where the graph reaches its target, or a
     * counterexample where it does not, fails the entry even when every checker agrees with it; so
     * does a counterexample shorter than the shortest path to the target, or longer from a checker
     * that reports a shortest one.
     */
    GRAPH {
        @Override
        boolean needsExpectation() {
            return true;
        }

        @Override
        CorpusVerdict judge(
                Map<CorpusStage, StageRecord> results, Optional<ExpectedOutcome> expected, Set<CorpusStage> exitStatusOnly) {
            var outcome = expected.orElseThrow(
                    () -> new IllegalArgumentException("the graph oracle judges against an expected outcome"));
            return agree(results, exitStatusOnly)
                            && results.entrySet().stream().allMatch(result -> outcome.admits(result.getKey(), result.getValue()))
                    ? CorpusVerdict.PASS
                    : CorpusVerdict.FAIL;
        }
    };

    /**
     * Judges the non-crash results of every checker that ran on one entry, against what the entry
     * predicts when the oracle needs a prediction. The checkers of {@code exitStatusOnly} tell only
     * a pass from an error (ADR 0033 §7), so their failure agrees with any counterexample or failure.
     *
     * @throws IllegalArgumentException if this oracle needs an expectation and none is given
     */
    abstract CorpusVerdict judge(
            Map<CorpusStage, StageRecord> results, Optional<ExpectedOutcome> expected, Set<CorpusStage> exitStatusOnly);

    /** Reports whether this oracle judges against the outcome an entry predicts. */
    boolean needsExpectation() {
        return false;
    }

    /**
     * Reports whether the results agree: every checker on whether it passed, the other checkers on
     * their verdicts, and completed searches on the states they reached.
     */
    private static boolean agree(Map<CorpusStage, StageRecord> results, Set<CorpusStage> exitStatusOnly) {
        var exact = results.entrySet().stream()
                .filter(result -> !exitStatusOnly.contains(result.getKey()))
                .map(result -> result.getValue().verdict())
                .distinct()
                .count();
        var passes = results.values().stream()
                .map(result -> result.verdict() == CorpusVerdict.PASS)
                .distinct()
                .count();
        return exact <= 1 && passes == 1
                && results.values().stream()
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
