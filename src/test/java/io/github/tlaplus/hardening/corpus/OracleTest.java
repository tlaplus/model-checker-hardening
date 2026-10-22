package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusVerdict.COUNTEREXAMPLE;
import static io.github.tlaplus.hardening.corpus.CorpusVerdict.FAIL;
import static io.github.tlaplus.hardening.corpus.CorpusVerdict.PASS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OracleTest {
    @Test
    void conformancePassesWhenTheCheckersAgree() {
        assertEquals(PASS, judge(Oracle.CONFORMANCE, verdicts(COUNTEREXAMPLE, COUNTEREXAMPLE)));
        assertEquals(FAIL, judge(Oracle.CONFORMANCE, verdicts(PASS, FAIL)));
    }

    /** A counterexample violates the relation even when every checker reports it (ADR 0016 §4). */
    @Test
    void metamorphicPassesWhenTheCheckersAgreeOnAnythingButACounterexample() {
        assertEquals(PASS, judge(Oracle.METAMORPHIC, verdicts(PASS, PASS)));
        assertEquals(PASS, judge(Oracle.METAMORPHIC, verdicts(FAIL, FAIL)));
        assertEquals(PASS, judge(Oracle.METAMORPHIC, verdicts(PASS)));
        assertEquals(FAIL, judge(Oracle.METAMORPHIC, verdicts(COUNTEREXAMPLE, COUNTEREXAMPLE)));
        assertEquals(FAIL, judge(Oracle.METAMORPHIC, verdicts(COUNTEREXAMPLE)));
        assertEquals(FAIL, judge(Oracle.METAMORPHIC, verdicts(PASS, FAIL)));
    }

    /** Two completed searches of one input reach the same states (ADR 0019). */
    @Test
    void everyOracleFailsCompletedSearchesThatReachDifferentNumbersOfStates() {
        for (var oracle : Oracle.values()) {
            assertEquals(PASS, judge(oracle, List.of(complete(7), complete(7))), oracle.name());
            assertEquals(FAIL, judge(oracle, List.of(complete(7), complete(8))), oracle.name());
            assertEquals(FAIL, judge(oracle, List.of(complete(7), complete(7), complete(8))), oracle.name());
        }
    }

    /** A checker that does not count distinct states, such as Apalache, is compared by verdict. */
    @Test
    void comparesTheCountsOfOnlyTheCheckersThatMeasureThem() {
        assertEquals(PASS, judge(Oracle.CONFORMANCE, List.of(complete(7), result(PASS))));
        assertEquals(PASS, judge(Oracle.CONFORMANCE, List.of(complete(7), complete(7), result(PASS))));
        assertEquals(FAIL, judge(Oracle.CONFORMANCE, List.of(complete(7), complete(8), result(PASS))));
    }

    /** A run stopped early explored a prefix that depends on its search order. */
    @Test
    void ignoresTheCountsOfSearchesThatStoppedEarly() {
        assertEquals(PASS, judge(Oracle.CONFORMANCE, List.of(
                stopped(COUNTEREXAMPLE, 3), stopped(COUNTEREXAMPLE, 5))));
        assertEquals(PASS, judge(Oracle.METAMORPHIC, List.of(stopped(FAIL, 3), stopped(FAIL, 5))));
    }

    /**
     * A counterexample where the graph reaches its target passes, and so does a failure, which a
     * partial term causes on any input; a pass where the target is reached does not (ADR 0023 §5).
     */
    @Test
    void theGraphOracleAdmitsTheExpectedVerdictOrAFailure() {
        var reached = Optional.of(ExpectedOutcome.counterexample(2));
        var missed = Optional.of(ExpectedOutcome.pass());
        assertEquals(PASS, judge(Oracle.GRAPH, verdicts(COUNTEREXAMPLE, COUNTEREXAMPLE), reached));
        assertEquals(PASS, judge(Oracle.GRAPH, verdicts(FAIL, FAIL), reached));
        assertEquals(PASS, judge(Oracle.GRAPH, verdicts(PASS), missed));
        assertEquals(PASS, judge(Oracle.GRAPH, verdicts(FAIL), missed));
        assertEquals(FAIL, judge(Oracle.GRAPH, verdicts(PASS, PASS), reached), "both checkers miss the target");
        assertEquals(FAIL, judge(Oracle.GRAPH, verdicts(COUNTEREXAMPLE), missed), "an unreachable target is reached");
        assertEquals(FAIL, judge(Oracle.GRAPH, verdicts(COUNTEREXAMPLE, FAIL), reached), "the checkers disagree");
    }

    @Test
    void onlyTheGraphOracleNeedsAnExpectation() {
        for (var oracle : Oracle.values()) {
            assertEquals(oracle == Oracle.GRAPH, oracle.needsExpectation(), oracle.name());
        }
        assertThrows(IllegalArgumentException.class, () -> judge(Oracle.GRAPH, verdicts(PASS), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckingPolicy(CheckerSet.DEFAULT, Oracle.GRAPH, ExpectedOutcomes.NONE));
    }

    /** Judges {@code results}, keyed by the checker branches in order, against a pass. */
    private static CorpusVerdict judge(Oracle oracle, List<StageRecord> results) {
        return judge(oracle, results, Optional.of(ExpectedOutcome.pass()));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static CorpusVerdict judge(Oracle oracle, List<StageRecord> results, Optional<ExpectedOutcome> expected) {
        var keyed = new EnumMap<CorpusStage, StageRecord>(CorpusStage.class);
        for (var index = 0; index < results.size(); index++) {
            keyed.put(CorpusStage.checkerBranches().get(index), results.get(index));
        }
        return oracle.judge(keyed, expected);
    }

    private static List<StageRecord> verdicts(CorpusVerdict... verdicts) {
        return Arrays.stream(verdicts).map(OracleTest::result).toList();
    }

    private static StageRecord result(CorpusVerdict verdict) {
        return record(verdict, Optional.empty());
    }

    private static StageRecord complete(long distinctStates) {
        return record(PASS, Optional.of(metrics(ExplorationPhase.COMPLETE, distinctStates)));
    }

    private static StageRecord stopped(CorpusVerdict verdict, long distinctStates) {
        return record(verdict, Optional.of(metrics(ExplorationPhase.EXPLORE, distinctStates)));
    }

    private static ExplorationMetrics metrics(ExplorationPhase phase, long distinctStates) {
        return ExplorationMetrics.builder()
                .phase(phase)
                .count(ExplorationCount.DISTINCT_STATES, distinctStates)
                .build();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static StageRecord record(CorpusVerdict verdict, Optional<ExplorationMetrics> metrics) {
        return new StageRecord(verdict, Instant.EPOCH, Instant.EPOCH, Optional.empty(), metrics);
    }
}
