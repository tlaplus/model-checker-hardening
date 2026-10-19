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
    private static final int SPINE = 3;
    /** Header, cumulative line, four input lines, the arrow into the parser, the parser and the fan-out label. */
    private static final int FIRST_BRANCH_ROW = 10;
    private static final int BRANCHES = CorpusStage.checkerBranches().size();

    @Test
    void flowFitsOneScreenAndShowsFanOutJoinAndFeedback() {
        var lines = RunDisplayFixture.render(RunDisplayFixture.sample(), new Size(80, 24));
        var output = RunDisplayFixture.plain(lines);
        assertEquals(FIRST_BRANCH_ROW + 2 * BRANCHES + 5, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.columnLength() <= 79));
        // Each count reserves one column for its change marker; elapsed times never get one.
        assertEquals("FuzzTLA RUNNING  Gen 3   Entries 1240   Elapsed 2m17s", lines.getFirst().toString());
        assertTrue(output.contains("each pass goes to every checker"));
        assertTrue(output.contains("every non-crash result"));
        assertTrue(output.contains("gate at generation end"));
        assertTrue(output.contains("mutation parents --> next generation INPUTS"));
        assertTrue(output.contains("PARSER  Queued 40   Pass 1180   Fail 18   Crash 2   Work 8s"));
        assertTrue(output.contains("AGGREGATOR  Agree 1060   Differ 28   Work 1s"));
        assertTrue(output.contains("QUALITY  Keep 220   Drop 500   Work 2s"));
        var apalache = FIRST_BRANCH_ROW + 2 * CorpusStage.checkerBranches().indexOf(CorpusStage.APALACHE);
        assertTrue(lines.get(apalache).toString().endsWith("APALACHE  Queued 86   Work 7m48s"), lines.get(apalache).toString());
        assertEquals("   |    Pass 1022   Cex 45   Fail 23   Crash 1", lines.get(apalache + 1).toString().stripTrailing());
        assertFalse(output.contains("AGGREGATOR  Queued"));
        assertFalse(output.contains("QUALITY  Queued"));
    }

    /** Every checker branch hangs off one spine, so the flow's width does not grow with them. */
    @Test
    void flowBranchesHangOffOneSpineInBothGlyphSets() {
        for (var glyphs : Glyphs.values()) {
            var lines = render(glyphs, new Size(80, 24)).stream().map(AttributedString::toString).toList();
            var arrow = glyphs.arrowDown().charAt(0);
            var vertical = glyphs.vertical().charAt(0);
            assertEquals(vertical, lines.get(6).charAt(SPINE));
            assertEquals(arrow, lines.get(7).charAt(SPINE));
            assertEquals(vertical, lines.get(FIRST_BRANCH_ROW - 1).charAt(SPINE));
            for (var branch = 0; branch < BRANCHES; branch++) {
                var line = lines.get(FIRST_BRANCH_ROW + 2 * branch);
                var stage = CorpusStage.checkerBranches().get(branch);
                assertTrue(line.startsWith("   " + glyphs.junction(true, true, false, true) + glyphs.arrowRight(3)
                        + " " + stage.displayName().toUpperCase(Locale.ROOT) + "  "), glyphs + ": " + line);
                var results = lines.get(FIRST_BRANCH_ROW + 2 * branch + 1);
                assertEquals(vertical, results.charAt(SPINE), glyphs + ": " + results);
                assertEquals(8, results.indexOf("Pass"), glyphs + ": " + results);
            }
            var join = FIRST_BRANCH_ROW + 2 * BRANCHES;
            assertEquals(SPINE, lines.get(join).indexOf(arrow), glyphs.name());
            assertEquals(SPINE, lines.get(join + 2).indexOf(arrow), glyphs.name());
        }
    }

    @Test
    void unicodeDrawsTheSameDiagramWithBoxLines() {
        var lines = render(Glyphs.UNICODE, new Size(80, 24)).stream().map(AttributedString::toString).toList();
        var ascii = render(Glyphs.ASCII, new Size(80, 24));
        assertEquals("   \u2502", lines.get(6));
        assertEquals("   \u25bc", lines.get(7));
        assertTrue(lines.get(FIRST_BRANCH_ROW).startsWith("   \u251c\u2500\u2500\u25b6 TLC"));
        assertTrue(lines.getLast().startsWith(
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
