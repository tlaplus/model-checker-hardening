package io.github.tlaplus.hardening.workflow.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityKeyTest {
    @Test
    void pinsTheComponentOrderOfAdr0010() {
        assertEquals(
                List.of(
                        ExplorationCount.PROJECTED_DEPTH,
                        ExplorationCount.PROJECTED_STATES,
                        ExplorationCount.ACTIONS_DISCOVERING,
                        ExplorationCount.MAX_STATE_NODES,
                        ExplorationCount.MAX_CARDINALITY,
                        ExplorationCount.MAX_NESTING),
                QualityKey.COMPONENTS.stream().map(QualityKey.Component::count).toList());
    }

    @Test
    void bucketsUnboundedCountsLogarithmically() {
        assertEquals(0, QualityKey.Scale.LOG2.bucket(0));
        assertEquals(1, QualityKey.Scale.LOG2.bucket(1));
        assertEquals(1, QualityKey.Scale.LOG2.bucket(2));
        assertEquals(2, QualityKey.Scale.LOG2.bucket(3));
        assertEquals(2, QualityKey.Scale.LOG2.bucket(6));
        assertEquals(3, QualityKey.Scale.LOG2.bucket(7));
        assertEquals(7, QualityKey.Scale.LINEAR.bucket(7));
    }

    @Test
    void comparesLexicographicallyWithTheDeeperEntryFirst() {
        var deep = ranked(metrics(3, 2, 1), 10, "b");
        var wide = ranked(metrics(2, 100, 5), 10, "a");
        assertTrue(QualityKey.BEST_FIRST.compare(deep, wide) < 0);
    }

    @Test
    void aBucketKeepsASmallStateDifferenceFromOutrankingTheNextComponent() {
        // 6 and 5 projected states share bucket 2, so more discovering actions win.
        var moreStates = ranked(metrics(2, 6, 1), 10, "a");
        var moreActions = ranked(metrics(2, 5, 2), 10, "b");
        assertTrue(QualityKey.BEST_FIRST.compare(moreActions, moreStates) < 0);
    }

    @Test
    void breaksTiesByShorterInputThenDigestAndTreatsAnAbsentCountAsZero() {
        var shorter = ranked(metrics(1, 1, 1), 4, "z");
        var longer = ranked(metrics(1, 1, 1), 5, "a");
        assertTrue(QualityKey.BEST_FIRST.compare(shorter, longer) < 0);
        var first = ranked(metrics(1, 1, 1), 4, "a");
        assertTrue(QualityKey.BEST_FIRST.compare(first, shorter) < 0);
        var unmeasured = ranked(ExplorationMetrics.builder().build(), 1, "a");
        var zero = ranked(metrics(0, 0, 0), 1, "a");
        assertEquals(0, QualityKey.BEST_FIRST.compare(unmeasured, zero));
    }

    private static QualityKey.Ranked ranked(ExplorationMetrics metrics, int inputBytes, String digest) {
        return new QualityKey.Ranked(metrics, inputBytes, digest);
    }

    private static ExplorationMetrics metrics(long depth, long states, long actions) {
        return ExplorationMetrics.builder()
                .count(ExplorationCount.PROJECTED_DEPTH, depth)
                .count(ExplorationCount.PROJECTED_STATES, states)
                .count(ExplorationCount.ACTIONS_DISCOVERING, actions)
                .build();
    }
}
