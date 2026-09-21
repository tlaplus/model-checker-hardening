package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

class ProgressLayoutTest {
    @Test
    void flowFitsOneScreenAndShowsFanOutJoinAndFeedback() {
        var lines = RunDisplayFixture.render(RunDisplayFixture.sample(), new Size(80, 24));
        var output = RunDisplayFixture.plain(lines);
        assertEquals(23, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() <= 79));
        // Each count reserves one column for its change marker; elapsed times never get one.
        assertEquals("FuzzTLA RUNNING  Gen 3   Entries 1240   Elapsed 2m17s", lines.getFirst().toString());
        assertTrue(output.contains("each pass goes to every checker"));
        assertTrue(output.contains("both non-crash results"));
        assertTrue(output.contains("gate at generation end"));
        assertTrue(output.contains("mutation parents --> next generation INPUTS"));
        assertTrue(output.contains("PARSER  Queued 40   Pass 1180   Fail 18   Crash 2   Work 8s"));
        assertTrue(output.contains("AGGREGATOR  Agree 1060   Differ 28   Work 1s"));
        assertTrue(output.contains("QUALITY  Keep 220   Drop 500   Work 2s"));
        var title = lines.get(12).toString();
        assertEquals(39, title.indexOf("APALACHE"));
        assertTrue(lines.get(14).toString().contains("Cex 45"));
        assertFalse(output.contains("AGGREGATOR  Queued"));
        assertFalse(output.contains("QUALITY  Queued"));
    }

    @Test
    void flowArrowsStartAtTheirConnectorJunctionsInBothGlyphSets() {
        for (var glyphs : Glyphs.values()) {
            var lines = render(glyphs, new Size(80, 24)).stream().map(AttributedString::toString).toList();
            var horizontal = glyphs.junction(false, false, true, true).charAt(0);
            var arrow = glyphs.arrowDown().charAt(0);
            var fanOut = lines.get(10);
            var branches = lines.get(11);
            for (var column = 0; column < Math.max(fanOut.length(), branches.length()); column++) {
                var junction = column < fanOut.length() && fanOut.charAt(column) != horizontal
                        && fanOut.charAt(column) != ' ';
                var branch = column < branches.length() && branches.charAt(column) == arrow;
                assertEquals(junction, branch, glyphs + " column " + column);
            }
            assertEquals(glyphs.vertical().charAt(0), lines.get(6).charAt(3));
            assertEquals(arrow, lines.get(7).charAt(3));
            assertEquals(glyphs.vertical().charAt(0), lines.get(9).charAt(3));
            var joinArrow = lines.get(18).indexOf(arrow);
            assertNotEquals(horizontal, lines.get(17).charAt(joinArrow), glyphs.name());
            assertEquals(joinArrow, lines.get(20).indexOf(arrow));
        }
    }

    @Test
    void unicodeDrawsTheSameDiagramWithBoxLines() {
        var lines = render(Glyphs.UNICODE, new Size(80, 24)).stream().map(AttributedString::toString).toList();
        var ascii = render(Glyphs.ASCII, new Size(80, 24));
        assertEquals("   \u2502", lines.get(6));
        assertEquals("   \u25bc", lines.get(7));
        assertEquals("   \u251c" + "\u2500".repeat(38) + "\u2510", lines.get(10));
        assertEquals("   \u2514" + "\u2500".repeat(14) + "\u252c" + "\u2500".repeat(23) + "\u2518", lines.get(17));
        assertTrue(lines.get(22).startsWith(
                "             \u2514\u2500\u2500\u25b6 mutation parents \u2500\u2500\u25b6 next generation"));
        for (var row = 0; row < lines.size(); row++) {
            assertEquals(ascii.get(row).columnLength(), new AttributedString(lines.get(row)).columnLength());
        }
        var table = RunDisplayFixture.plain(render(Glyphs.UNICODE, new Size(60, 16)));
        assertTrue(table.contains("Inputs \u2500\u25b6 Parser \u2500\u25b6 checkers"));
    }

    private static List<AttributedString> render(Glyphs glyphs, Size size) {
        var source = RunDisplayFixture.sample();
        return ProgressLayout.render(new RunText(RunDisplayFixture.values(source), Precision.COMPACT,
                new RunPalette(false, false, glyphs), RunText.Highlights.live(Map.of())), size, true);
    }

    @Test
    void compactSnapshotPreservesStageAndVerdictIdentities() {
        var lines = RunDisplayFixture.render(RunDisplayFixture.sample(), new Size(60, 16));
        assertEquals(12, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() <= 59));
        assertEquals("""
                FuzzTLA RUNNING  Gen 3   Entries 1240
                Elapsed 2m17s; cumulative totals / time
                Inputs -> Parser -> checkers -> join -> Quality
                Stage          Queued  Pass    Cex     Fail    Crash
                INPUTS  Admitted 1240  / Attempts 1860
                PARSER         40      1180    -       18      2
                TLC            20      1088    45      23      2
                APALACHE       86      1022    45      23      1
                AGGREGATOR  Agree 1060   Differ 28
                QUALITY  Keep 220   Drop 500
                `--> mutation parents --> next generation INPUTS
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
    void markersReplaceReservedBlanksWithoutMovingLaterText() {
        var source = RunDisplayFixture.sample();
        var values = RunDisplayFixture.values(source);
        var queue = new RunMetric.Queue(CorpusStage.PARSER);
        var pass = new RunMetric.Result(CorpusStage.PARSER, CorpusVerdict.PASS);
        var quiet = ProgressLayout.render(new RunText(values, Precision.COMPACT, new RunPalette(false, false, Glyphs.UNICODE),
                RunText.Highlights.live(Map.of())), new Size(80, 24), true);
        var marked = ProgressLayout.render(new RunText(values, Precision.COMPACT, new RunPalette(false, false, Glyphs.UNICODE),
                RunText.Highlights.live(Map.of(queue, RunValue.Direction.DOWN, pass, RunValue.Direction.UP))),
                new Size(80, 24), true);
        assertEquals("PARSER  Queued 40\u2193  Pass 1180\u2191  Fail 18   Crash 2   Work 8s", marked.get(8).toString());
        assertEquals(quiet.get(8).columnLength(), marked.get(8).columnLength());
        for (var row = 0; row < quiet.size(); row++) {
            if (row != 8) {
                assertEquals(quiet.get(row), marked.get(row));
            }
        }
    }

    @Test
    void emptyRichnessAndDisabledMutationAreExplicit() {
        var source = RunDisplayFixture.empty();
        var output = RunDisplayFixture.plain(ProgressLayout.render(
                new RunText(RunDisplayFixture.values(source), Precision.COMPACT, RunPalette.PLAIN,
                        RunText.Highlights.live(Map.of())),
                new Size(80, 24), false));
        assertTrue(output.contains("Richness min n/a  / avg n/a  / max n/a"));
        assertTrue(output.contains("Mutation feedback disabled"));
    }

    @Test
    void stylesOnlyChangedValuesAndKeepsSemanticColors() {
        var source = RunDisplayFixture.sample();
        var palette = new RunPalette(true, true, Glyphs.UNICODE);
        var key = new RunMetric.Result(CorpusStage.TLC, CorpusVerdict.COUNTEREXAMPLE);
        var text = new RunText(RunDisplayFixture.values(source), Precision.COMPACT, palette,
                RunText.Highlights.live(Map.of(key, RunValue.Direction.UP)));
        var line = text.line().result(CorpusStage.TLC, CorpusVerdict.COUNTEREXAMPLE).text(" ")
                .result(CorpusStage.TLC, CorpusVerdict.FAIL).build();
        assertEquals("Cex 45\u2191 Fail 23 ", line.toString());
        var changed = AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA).bold();
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA), line.styleAt(0));
        assertEquals(changed, line.styleAt(4));
        assertEquals(changed, line.styleAt(6), "the marker shares the value's emphasis");
        assertEquals(AttributedStyle.DEFAULT, line.styleAt(7));
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW), line.styleAt(8));
        assertEquals(AttributedStyle.DEFAULT, text.line().value(RunMetric.Field.TOTAL_ELAPSED).build().styleAt(0));
    }
}
