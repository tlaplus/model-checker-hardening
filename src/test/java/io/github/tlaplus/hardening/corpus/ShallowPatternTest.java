package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ShallowPatternTest {
    @Test
    void configNamesAndOrderFollowTheCalibrationTable() {
        assertEquals(
                List.of("vacuous_pass", "initial_state_violation", "early_failure",
                        "no_discovering_action", "counter_only_progress"),
                Arrays.stream(ShallowPattern.values()).map(ShallowPattern::configName).toList());
        for (var pattern : ShallowPattern.values()) {
            assertEquals(Optional.of(pattern), ShallowPattern.fromConfigName(pattern.configName()));
        }
    }

    @Test
    void recognizesEachRowOfTheCalibrationTable() {
        assertEquals(Set.of(ShallowPattern.VACUOUS_PASS),
                matching(record(CorpusVerdict.PASS, ExplorationMetrics.builder()
                        .phase(ExplorationPhase.COMPLETE).count(ExplorationCount.INIT_STATES, 0))));
        assertEquals(Set.of(ShallowPattern.INITIAL_STATE_VIOLATION),
                matching(record(CorpusVerdict.COUNTEREXAMPLE, ExplorationMetrics.builder()
                        .phase(ExplorationPhase.INIT))));
        assertEquals(Set.of(ShallowPattern.EARLY_FAILURE),
                matching(record(CorpusVerdict.FAIL, ExplorationMetrics.builder()
                        .phase(ExplorationPhase.INIT))));
        assertEquals(Set.of(ShallowPattern.NO_DISCOVERING_ACTION),
                matching(record(CorpusVerdict.PASS, deep().count(ExplorationCount.ACTIONS_DISCOVERING, 0))));
        assertEquals(Set.of(ShallowPattern.COUNTER_ONLY_PROGRESS),
                matching(record(CorpusVerdict.PASS, deep().count(ExplorationCount.PROJECTED_STATES, 1))));
        assertEquals(Set.of(), matching(record(CorpusVerdict.PASS, deep())));
    }

    @Test
    void unmeasuredValuesMatchNoPattern() {
        assertEquals(Set.of(), matching(record(CorpusVerdict.PASS, ExplorationMetrics.builder())));
        assertEquals(Set.of(), matching(new StageRecord(
                CorpusVerdict.PASS, Instant.EPOCH, Instant.EPOCH)));
    }

    private static ExplorationMetrics.Builder deep() {
        return ExplorationMetrics.builder()
                .phase(ExplorationPhase.COMPLETE)
                .count(ExplorationCount.INIT_STATES, 1)
                .count(ExplorationCount.ACTIONS_DISCOVERING, 2)
                .count(ExplorationCount.PROJECTED_STATES, 4);
    }

    private static StageRecord record(CorpusVerdict verdict, ExplorationMetrics.Builder metrics) {
        var failure = verdict == CorpusVerdict.FAIL
                ? Optional.of(new CheckerFailure(CheckerFailureCode.SPEC_EVAL, Optional.empty()))
                : Optional.<CheckerFailure>empty();
        return new StageRecord(verdict, Instant.EPOCH, Instant.EPOCH, failure, Optional.of(metrics.build()));
    }

    private static Set<ShallowPattern> matching(StageRecord tlc) {
        return Arrays.stream(ShallowPattern.values())
                .filter(pattern -> pattern.matches(tlc))
                .collect(Collectors.toSet());
    }
}
