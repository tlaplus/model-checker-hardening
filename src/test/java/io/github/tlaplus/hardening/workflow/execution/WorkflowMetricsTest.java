package io.github.tlaplus.hardening.workflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowMetricsTest {
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
                10,
                3,
                2,
                1,
                2,
                2.0,
                6.0,
                4.0);
        var metrics = new WorkflowMetrics(previous, 7);

        var generator = metrics.generator();
        generator.recordAttempt();
        generator.recordRejection();
        generator.recordRichnessRejection();
        generator.recordDuplicate();
        generator.recordAdmission(8.0);

        var summary = generator.summary(42);
        assertEquals(8, summary.generated());
        assertEquals(11, summary.attempts());
        assertEquals(4, summary.rejected());
        assertEquals(3, summary.richnessRejected());
        assertEquals(2, summary.duplicates());
        assertEquals(3, summary.richnessSamples());
        assertEquals(2.0, summary.minimumRichness());
        assertEquals(8.0, summary.maximumRichness());
        assertEquals(16.0 / 3.0, summary.averageRichness(), 1e-12);
        assertEquals(Duration.ofNanos(20), summary.elapsed());

        var saved = metrics.snapshot(Duration.ofNanos(25));
        assertEquals(125, saved.totalElapsedNanos());
        assertEquals(30, saved.stageElapsedNanos(CorpusStage.PARSER));
        assertEquals(40, saved.stageElapsedNanos(CorpusStage.TLC));
        assertEquals(50, saved.stageElapsedNanos(CorpusStage.APALACHE));
        assertEquals(60, saved.stageElapsedNanos(CorpusStage.AGGREGATOR));
        assertEquals(11, saved.generatorAttempts());
        assertEquals(3, saved.richnessSamples());
        assertEquals(16.0 / 3.0, saved.averageRichness(), 1e-12);
    }
}
