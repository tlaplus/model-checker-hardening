package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProgressChangesTest {
    @Test
    void comparesUnroundedValuesInBothDirectionsAndRenewsExpiry() {
        var changes = new ProgressChanges();
        var queue = new RunMetric.Queue(CorpusStage.APALACHE);
        var count = RunMetric.Field.GENERATED;
        var first = Map.<RunMetric, RunValue>of(count, new RunValue.Count(10_001), queue, new RunValue.Count(40));
        changes.observe(first, 0);
        assertTrue(changes.highlighted(0).isEmpty());

        var next = Map.<RunMetric, RunValue>of(count, new RunValue.Count(10_002), queue, new RunValue.Count(39));
        assertEquals(first.get(count).format(Precision.COMPACT), next.get(count).format(Precision.COMPACT));
        changes.observe(next, 1_000_000_000L);
        assertEquals(Set.of(count, queue), changes.highlighted(1_000_000_000L));
        changes.observe(next, 1_500_000_000L);
        assertEquals(Set.of(count, queue), changes.highlighted(1_999_999_999L));
        assertTrue(changes.highlighted(2_000_000_000L).isEmpty());

        changes.observe(first, 2_000_000_001L);
        changes.observe(next, 2_500_000_001L);
        assertEquals(Set.of(count, queue), changes.highlighted(3_000_000_001L));
        assertTrue(changes.highlighted(3_500_000_001L).isEmpty());
    }

    @Test
    void tracksRichnessGenerationAndStatusButNotElapsedClocks() {
        var changes = new ProgressChanges();
        var values = new HashMap<>(RunDisplayFixture.values(RunDisplayFixture.empty()));
        changes.observe(values, 0);
        values.put(RunMetric.Field.MIN_RICHNESS, RunValue.Richness.of(1, 0.00001));
        values.put(RunMetric.Field.GENERATION, new RunValue.Count(1));
        values.put(RunMetric.State.STATUS, new RunValue.Status(WorkflowProgress.Phase.FINALIZING));
        values.put(RunMetric.Field.TOTAL_ELAPSED, new RunValue.Elapsed(Duration.ofSeconds(1)));
        values.put(new RunMetric.StageElapsed(CorpusStage.TLC), new RunValue.Elapsed(Duration.ofSeconds(1)));
        changes.observe(values, 10);
        assertEquals(Set.of(RunMetric.Field.MIN_RICHNESS, RunMetric.Field.GENERATION,
                RunMetric.State.STATUS), changes.highlighted(10));
        assertEquals(changes.highlighted(10), changes.highlighted(20));
    }
}
