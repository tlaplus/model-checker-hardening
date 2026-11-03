package io.github.tlaplus.hardening.workflow.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.corpus.Ranking;
import io.github.tlaplus.hardening.corpus.RankingInput;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityKeyTest {
    @Test
    void pinsTheComponentOrderOfAdr0010() {
        assertEquals(
                List.of(
                        exploration(ExplorationCount.PROJECTED_DEPTH),
                        exploration(ExplorationCount.PROJECTED_STATES),
                        Ranking.Measure.Replayed.TEMPORAL_SHAPES,
                        exploration(ExplorationCount.ACTIONS_DISCOVERING),
                        exploration(ExplorationCount.MAX_STATE_NODES),
                        exploration(ExplorationCount.MAX_CARDINALITY),
                        exploration(ExplorationCount.MAX_NESTING)),
                Ranking.DEPTH.components().stream().map(Ranking.Component::measure).toList());
        assertTrue(Ranking.DEPTH.replays());
    }

    @Test
    void pinsTheComponentOrderOfAdr0024() {
        assertEquals(
                List.of(
                        exploration(ExplorationCount.DATA_ACTIONS),
                        exploration(ExplorationCount.PROJECTED_STATES),
                        exploration(ExplorationCount.PROJECTED_DEPTH),
                        exploration(ExplorationCount.MAX_STATE_NODES),
                        exploration(ExplorationCount.MAX_CARDINALITY),
                        exploration(ExplorationCount.MAX_NESTING)),
                Ranking.ACTION_COVERAGE.components().stream().map(Ranking.Component::measure).toList());
        assertFalse(Ranking.ACTION_COVERAGE.replays());
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

    /** ADR 0028 §3: temporal shapes decide after the explored states, before discovering actions. */
    @Test
    void ranksTemporalShapesAfterStatesAndBeforeActions() {
        var depth = Ranking.DEPTH;
        var richProperty = ranked(metrics(2, 5, 1), 2, 10, "b");
        var moreActions = ranked(metrics(2, 5, 3), 0, 10, "a");
        assertTrue(QualityKey.bestFirst(depth).compare(richProperty, moreActions) < 0);
        var deeper = ranked(metrics(3, 5, 1), 0, 10, "c");
        assertTrue(QualityKey.bestFirst(depth).compare(deeper, richProperty) < 0, "depth still comes first");
        // 6 and 3 shapes share bucket 2, so more discovering actions win.
        var sixShapes = ranked(metrics(2, 5, 1), 6, 10, "d");
        var threeShapesMoreActions = ranked(metrics(2, 5, 3), 3, 10, "e");
        assertTrue(QualityKey.bestFirst(depth).compare(threeShapesMoreActions, sixShapes) < 0);
        assertTrue(QualityKey.bestFirst(Ranking.ACTION_COVERAGE).compare(moreActions, richProperty) < 0,
                "action coverage ignores the shapes");
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
        return ranked(metrics, 0, inputBytes, digest);
    }

    private static QualityKey.Ranked ranked(
            ExplorationMetrics metrics, long temporalShapes, int inputBytes, String digest) {
        return new QualityKey.Ranked(new RankingInput(metrics, temporalShapes), inputBytes, digest);
    }

    private static Ranking.Measure exploration(ExplorationCount count) {
        return new Ranking.Measure.Exploration(count);
    }

    private static ExplorationMetrics metrics(long depth, long states, long actions) {
        return ExplorationMetrics.builder()
                .count(ExplorationCount.PROJECTED_DEPTH, depth)
                .count(ExplorationCount.PROJECTED_STATES, states)
                .count(ExplorationCount.ACTIONS_DISCOVERING, actions)
                .build();
    }
}
