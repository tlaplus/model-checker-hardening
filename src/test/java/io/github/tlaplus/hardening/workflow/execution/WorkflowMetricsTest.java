package io.github.tlaplus.hardening.workflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.common.GeneratorAggregate.Richness;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowMetricsTest {
    @Test
    void summariesSnapshotTheSharedAggregateAndValidateOnlyInvocationFields() {
        var metrics = new GeneratorStatistics(CorpusRunStatistics.empty(), 0);
        metrics.recordAdmission(4);
        var previous = metrics.summary(42);
        metrics.recordAdmission(8);
        assertEquals(new Richness(1, 4, 4, 4), previous.aggregate().richness());
        assertEquals(1, previous.generated());
        assertEquals(2, metrics.summary(42).generated());
        assertEquals(metrics.snapshot(), metrics.summary(42).aggregate());
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratorSummary(-1, 0, GeneratorAggregate.empty(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratorSummary(0, -1, GeneratorAggregate.empty(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratorSummary(0, 0, GeneratorAggregate.empty(), Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class, () -> new GeneratorSummary(0, 0, null, Duration.ZERO));
    }

    @Test
    void combinesPersistedAndCurrentGeneratorStatistics() {
        var previous = new CorpusRunStatistics(
                100,
                20,
                Map.of(
                        CorpusStage.PARSER, 30L,
                        CorpusStage.TLC, 40L,
                        CorpusStage.APALACHE, 50L,
                        CorpusStage.AGGREGATOR, 60L),
                new GeneratorAggregate(10, 3, 2, 1, new Richness(2, 2.0, 6.0, 4.0)));
        var metrics = new WorkflowMetrics(previous, 7);

        var generator = metrics.generator();
        generator.recordAttempt();
        generator.recordRejection();
        generator.recordRichnessRejection();
        generator.recordDuplicate();
        generator.recordAdmission(8.0);

        var summary = generator.summary(42);
        assertEquals(8, summary.generated());
        var aggregate = summary.aggregate();
        assertEquals(11, aggregate.attempts());
        assertEquals(4, aggregate.rejected());
        assertEquals(3, aggregate.richnessRejected());
        assertEquals(2, aggregate.duplicates());
        assertEquals(3, aggregate.richness().samples());
        assertEquals(2.0, aggregate.richness().minimum());
        assertEquals(8.0, aggregate.richness().maximum());
        assertEquals(16.0 / 3.0, aggregate.richness().average(), 1e-12);
        assertEquals(Duration.ofNanos(20), summary.elapsed());

        var saved = metrics.snapshot(Duration.ofNanos(25));
        assertEquals(125, saved.totalElapsedNanos());
        assertEquals(30, saved.stageElapsedNanos(CorpusStage.PARSER));
        assertEquals(40, saved.stageElapsedNanos(CorpusStage.TLC));
        assertEquals(50, saved.stageElapsedNanos(CorpusStage.APALACHE));
        assertEquals(60, saved.stageElapsedNanos(CorpusStage.AGGREGATOR));
        assertEquals(aggregate, saved.generator());
        assertEquals(aggregate, generator.snapshot());
    }
}
