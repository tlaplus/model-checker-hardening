package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RunTableTest {
    @Test
    void finalReportContainsExactCumulativeValuesAndAllStageFields() {
        var source = RunDisplayFixture.sample();
        var output = RunDisplayFixture.plain(
                RunTable.finished(Path.of("corpus"), RunDisplayFixture.finished(source), RunPalette.PLAIN));
        assertTrue(output.contains("Corpus entries: 1240"));
        assertTrue(output.contains("  Generated inputs: 1240"));
        assertTrue(output.contains("  Candidate attempts: 1860"));
        assertTrue(output.contains("  Known defects: 10"));
        assertTrue(output.contains("  Min richness: 0"));
        assertTrue(output.contains("  Avg richness: 4.5"));
        assertTrue(output.contains("  Max richness: 19"));
        assertTrue(output.contains("  Generator elapsed: 15s"));
        assertTrue(output.contains("Total elapsed: 2m 17s"));
        assertTrue(output.contains("Stop reason: COMPLETED"));
        assertTrue(output.contains("Cex 45"));
        assertTrue(output.contains("Agree 1060"));
        assertTrue(output.contains("Drop 500"));
        assertTrue(output.contains("  Summed worker time: 7m 48s"));
        assertFalse(output.contains("\u001b"));
        assertFalse(output.toLowerCase(Locale.ROOT).contains("seed"));
        assertEquals(CorpusStage.values().length, output.lines().filter(line -> line.startsWith("  Queued:")).count());
    }

    @Test
    void exactPrecisionKeepsFullCounts() {
        var huge = new RunText(RunDisplayFixture.values(RunDisplayFixture.extreme()), Precision.EXACT,
                RunPalette.PLAIN, Set.of()).line().value(RunMetric.Field.GENERATED).build();
        assertEquals(Long.toString(Long.MAX_VALUE), huge.toString());
    }
}
