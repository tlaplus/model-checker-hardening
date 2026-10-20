package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusVerdict.COUNTEREXAMPLE;
import static io.github.tlaplus.hardening.corpus.CorpusVerdict.FAIL;
import static io.github.tlaplus.hardening.corpus.CorpusVerdict.PASS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OracleTest {
    @Test
    void conformancePassesWhenTheCheckersAgree() {
        assertEquals(PASS, Oracle.CONFORMANCE.judge(verdicts(COUNTEREXAMPLE, COUNTEREXAMPLE)));
        assertEquals(FAIL, Oracle.CONFORMANCE.judge(verdicts(PASS, FAIL)));
    }

    /** A counterexample violates the relation even when every checker reports it (ADR 0016 §4). */
    @Test
    void metamorphicPassesWhenTheCheckersAgreeOnAnythingButACounterexample() {
        assertEquals(PASS, Oracle.METAMORPHIC.judge(verdicts(PASS, PASS)));
        assertEquals(PASS, Oracle.METAMORPHIC.judge(verdicts(FAIL, FAIL)));
        assertEquals(PASS, Oracle.METAMORPHIC.judge(verdicts(PASS)));
        assertEquals(FAIL, Oracle.METAMORPHIC.judge(verdicts(COUNTEREXAMPLE, COUNTEREXAMPLE)));
        assertEquals(FAIL, Oracle.METAMORPHIC.judge(verdicts(COUNTEREXAMPLE)));
        assertEquals(FAIL, Oracle.METAMORPHIC.judge(verdicts(PASS, FAIL)));
    }

    /** Two completed searches of one input reach the same states (ADR 0019). */
    @Test
    void everyOracleFailsCompletedSearchesThatReachDifferentNumbersOfStates() {
        for (var oracle : Oracle.values()) {
            assertEquals(PASS, oracle.judge(List.of(complete(7), complete(7))), oracle.name());
            assertEquals(FAIL, oracle.judge(List.of(complete(7), complete(8))), oracle.name());
            assertEquals(FAIL, oracle.judge(List.of(complete(7), complete(7), complete(8))), oracle.name());
        }
    }

    /** A checker that does not count distinct states, such as Apalache, is compared by verdict. */
    @Test
    void comparesTheCountsOfOnlyTheCheckersThatMeasureThem() {
        assertEquals(PASS, Oracle.CONFORMANCE.judge(List.of(complete(7), result(PASS))));
        assertEquals(PASS, Oracle.CONFORMANCE.judge(List.of(complete(7), complete(7), result(PASS))));
        assertEquals(FAIL, Oracle.CONFORMANCE.judge(List.of(complete(7), complete(8), result(PASS))));
    }

    /** A run stopped early explored a prefix that depends on its search order. */
    @Test
    void ignoresTheCountsOfSearchesThatStoppedEarly() {
        assertEquals(PASS, Oracle.CONFORMANCE.judge(List.of(
                stopped(COUNTEREXAMPLE, 3), stopped(COUNTEREXAMPLE, 5))));
        assertEquals(PASS, Oracle.METAMORPHIC.judge(List.of(stopped(FAIL, 3), stopped(FAIL, 5))));
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
