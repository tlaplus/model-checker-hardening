package io.github.tlaplus.hardening.workflow.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.corpus.Ranking;
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
                Ranking.DEPTH.components().stream().map(Ranking.Component::count).toList());
    }

    @Test
    void pinsTheComponentOrderOfAdr0024() {
        assertEquals(
                List.of(
                        ExplorationCount.DATA_ACTIONS,
                        ExplorationCount.PROJECTED_STATES,
                        ExplorationCount.PROJECTED_DEPTH,
                        ExplorationCount.MAX_STATE_NODES,
                        ExplorationCount.MAX_CARDINALITY,
                        ExplorationCount.MAX_NESTING),
                Ranking.ACTION_COVERAGE.components().stream().map(Ranking.Component::count).toList());
        assertEquals(Ranking.Scale.LOG2, Ranking.ACTION_COVERAGE.components().getFirst().scale());
    }

    /** ADR 0024 §2: a walk with more data actions outranks a longer one, by bucket of data actions. */
    @Test
    void actionCoverageRanksDataActionsBeforeDepthAndBucketsThem() {
        var actions = Ranking.ACTION_COVERAGE;
        var richer = ranked(coverage(4, 5, 3), 10, "b");
        var deeper = ranked(coverage(1, 5, 30), 10, "a");
        assertTrue(QualityKey.bestFirst(actions).compare(richer, deeper) < 0);
        // 11 and 10 data actions share bucket 4, so 60 valuations beat 3.
        var moreActions = ranked(coverage(11, 3, 5), 10, "a");
        var moreValuations = ranked(coverage(10, 60, 5), 10, "b");
        assertTrue(QualityKey.bestFirst(actions).compare(moreValuations, moreActions) < 0);
        assertTrue(QualityKey.bestFirst(Ranking.DEPTH).compare(deeper, richer) < 0, "the depth ranking prefers depth");
    }

    @Test
    void bucketsUnboundedCountsLogarithmically() {
        assertEquals(0, Ranking.Scale.LOG2.bucket(0));
        assertEquals(1, Ranking.Scale.LOG2.bucket(1));
        assertEquals(1, Ranking.Scale.LOG2.bucket(2));
        assertEquals(2, Ranking.Scale.LOG2.bucket(3));
        assertEquals(2, Ranking.Scale.LOG2.bucket(6));
        assertEquals(3, Ranking.Scale.LOG2.bucket(7));
        assertEquals(7, Ranking.Scale.LINEAR.bucket(7));
    }

    @Test
    void comparesLexicographicallyWithTheDeeperEntryFirst() {
        var deep = ranked(metrics(3, 2, 1), 10, "b");
        var wide = ranked(metrics(2, 100, 5), 10, "a");
        assertTrue(QualityKey.bestFirst(Ranking.DEPTH).compare(deep, wide) < 0);
    }

    @Test
    void aBucketKeepsASmallStateDifferenceFromOutrankingTheNextComponent() {
        // 6 and 5 projected states share bucket 2, so more discovering actions win.
        var moreStates = ranked(metrics(2, 6, 1), 10, "a");
        var moreActions = ranked(metrics(2, 5, 2), 10, "b");
        assertTrue(QualityKey.bestFirst(Ranking.DEPTH).compare(moreActions, moreStates) < 0);
    }

    @Test
    void breaksTiesByShorterInputThenDigestAndTreatsAnAbsentCountAsZero() {
        var shorter = ranked(metrics(1, 1, 1), 4, "z");
        var longer = ranked(metrics(1, 1, 1), 5, "a");
        assertTrue(QualityKey.bestFirst(Ranking.DEPTH).compare(shorter, longer) < 0);
        var first = ranked(metrics(1, 1, 1), 4, "a");
        assertTrue(QualityKey.bestFirst(Ranking.DEPTH).compare(first, shorter) < 0);
        var unmeasured = ranked(ExplorationMetrics.builder().build(), 1, "a");
        var zero = ranked(metrics(0, 0, 0), 1, "a");
        assertEquals(0, QualityKey.bestFirst(Ranking.DEPTH).compare(unmeasured, zero));
    }

    private static ExplorationMetrics coverage(long dataActions, long states, long depth) {
        return ExplorationMetrics.builder()
                .count(ExplorationCount.DATA_ACTIONS, dataActions)
                .count(ExplorationCount.PROJECTED_STATES, states)
                .count(ExplorationCount.PROJECTED_DEPTH, depth)
                .build();
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
