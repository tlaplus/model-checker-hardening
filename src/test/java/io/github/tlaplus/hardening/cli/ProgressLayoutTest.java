package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

class ProgressLayoutTest {
    @Test
    void flowFitsOneScreenAndShowsFanOutJoinAndFeedback() {
        var lines = RunDisplayFixture.render(RunDisplayFixture.sample(), new Size(80, 24));
        var output = RunDisplayFixture.plain(lines);
        assertEquals(22, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() <= 79));
        assertEquals("FuzzTLA RUNNING  Gen 3  Entries 1240  Elapsed 2m17s", lines.getFirst().toString());
        assertTrue(output.contains("each pass goes to every checker"));
        assertTrue(output.contains("both non-crash results"));
        assertTrue(output.contains("gate at generation end"));
        assertTrue(output.contains("mutation parents --> next generation INPUTS"));
        assertTrue(output.contains("PARSER  Queued 40  Pass 1180  Fail 18  Crash 2  Work 8s"));
        assertTrue(output.contains("AGGREGATOR  Agree 1060  Differ 28  Work 1s"));
        assertTrue(output.contains("QUALITY  Keep 220  Drop 500  Work 2s"));
        var title = lines.get(11).toString();
        assertEquals(39, title.indexOf("APALACHE"));
        assertTrue(lines.get(13).toString().contains("Cex 45"));
        assertFalse(output.contains("AGGREGATOR  Queued"));
        assertFalse(output.contains("QUALITY  Queued"));
    }

    @Test
    void flowArrowsStartAtTheirConnectorJunctions() {
        var lines = RunDisplayFixture.render(RunDisplayFixture.sample(), new Size(80, 24)).stream()
                .map(AttributedString::toString).toList();
        var fanOut = lines.get(9);
        var arrows = lines.get(10);
        for (var column = 0; column < Math.max(fanOut.length(), arrows.length()); column++) {
            var junction = column < fanOut.length() && fanOut.charAt(column) == '+';
            var arrow = column < arrows.length() && arrows.charAt(column) == 'v';
            assertEquals(junction, arrow, "column " + column);
        }
        assertEquals('v', lines.get(6).charAt(fanOut.indexOf('+')));
        var joinArrow = lines.get(17).indexOf('v');
        assertEquals('+', lines.get(16).charAt(joinArrow));
        assertEquals(joinArrow, lines.get(19).indexOf('v'));
    }

    @Test
    void compactSnapshotPreservesStageAndVerdictIdentities() {
        var lines = RunDisplayFixture.render(RunDisplayFixture.sample(), new Size(60, 16));
        assertEquals(13, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() <= 59));
        assertEquals("""
                FuzzTLA RUNNING  Gen 3  Entries 1240
                Elapsed 2m17s; cumulative totals / time
                Inputs -> Parser -> checkers -> join -> Quality
                Stage          Queued  Pass    Cex     Fail    Crash
                INPUTS  Admitted 1240 / Attempts 1860
                PARSER         40      1180    -       18      2
                TLC            20      1088    45      23      2
                APALACHE       86      1022    45      23      1
                AGGREGATOR  Agree 1060  Differ 28
                QUALITY  Keep 220  Drop 500
                `--> mutation parents --> next generation INPUTS
                Queued: waiting now; Cex: counterexamples
                Full statistics at completion""", RunDisplayFixture.plain(lines));
    }

    @Test
    void extremeValuesAndEveryTerminalSizeStayWithinVisibleBounds() {
        for (var size : List.of(new Size(80, 24), new Size(120, 40), new Size(60, 16),
                new Size(79, 24), new Size(80, 23), new Size(40, 10), new Size(1, 1), new Size(0, 0))) {
            for (var progress : List.of(RunDisplayFixture.extreme(), RunDisplayFixture.empty())) {
                var lines = RunDisplayFixture.render(progress, size);
                assertTrue(lines.size() <= Math.max(0, size.getRows() - 1));
                assertTrue(lines.stream().allMatch(line -> line.columnLength() <= Math.max(0, size.getColumns() - 1)));
                if (size.getColumns() >= 60 && size.getRows() >= 16) {
                    var rendered = RunDisplayFixture.plain(lines);
                    for (var stage : CorpusStage.values()) {
                        assertTrue(rendered.contains(stage.displayName().toUpperCase(Locale.ROOT)));
                    }
                }
            }
        }
        assertEquals(1, RunDisplayFixture.render(RunDisplayFixture.sample(), new Size(40, 10)).size());
    }

    @Test
    void emptyRichnessAndDisabledMutationAreExplicit() {
        var source = RunDisplayFixture.empty();
        var output = RunDisplayFixture.plain(ProgressLayout.render(
                new RunText(RunDisplayFixture.values(source), Precision.COMPACT, RunPalette.PLAIN, Set.of()),
                new Size(80, 24), false));
        assertTrue(output.contains("Richness min n/a / avg n/a / max n/a"));
        assertTrue(output.contains("Mutation feedback disabled"));
    }

    @Test
    void stylesOnlyChangedValuesAndKeepsSemanticColors() {
        var source = RunDisplayFixture.sample();
        var palette = new RunPalette(true, true, true);
        var key = new RunMetric.Result(CorpusStage.TLC, CorpusVerdict.COUNTEREXAMPLE);
        var text = new RunText(RunDisplayFixture.values(source), Precision.COMPACT, palette, Set.of(key));
        var line = text.line().result(CorpusStage.TLC, CorpusVerdict.COUNTEREXAMPLE).text(" ")
                .result(CorpusStage.TLC, CorpusVerdict.FAIL).build();
        assertEquals("Cex 45 Fail 23", line.toString());
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA), line.styleAt(0));
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA).bold().inverse(), line.styleAt(4));
        assertEquals(AttributedStyle.DEFAULT, line.styleAt(6));
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW), line.styleAt(7));
        assertEquals(AttributedStyle.DEFAULT, text.line().value(RunMetric.Field.TOTAL_ELAPSED).build().styleAt(0));
    }
}
