package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

class RunPaletteTest {
    @Test
    void respectsTerminalCapabilitiesAndNoColorWithoutRemovingMonochromeEmphasis() throws Exception {
        try (var fixture = RunTestTerminal.open("xterm-256color")) {
            var terminal = fixture.terminal();
            assertTrue(RunTerminal.supportsDisplay(terminal));
            assertTrue(RunPalette.detect(terminal, null).colors());
            assertTrue(RunPalette.detect(terminal, "").colors());
            var plain = RunPalette.detect(terminal, "1");
            assertFalse(plain.colors());
            assertEquals(AttributedStyle.DEFAULT.bold(), plain.style(RunPalette.Tone.BAD, true));
            assertEquals(Glyphs.UNICODE, plain.glyphs());
        }
        try (var fixture = RunTestTerminal.open("dumb")) {
            assertFalse(RunTerminal.supportsDisplay(fixture.terminal()));
            var dumb = RunPalette.detect(fixture.terminal(), null);
            assertFalse(dumb.colors());
            assertFalse(dumb.bold());
        }
        assertEquals(AttributedStyle.BOLD, new RunPalette(false, true, Glyphs.ASCII)
                .style(RunPalette.Tone.NORMAL, true));
    }

    @Test
    void keepsZeroAdverseCountsAndDropsNeutral() {
        var fail = new RunMetric.Result(CorpusStage.TLC, CorpusVerdict.FAIL);
        var differ = new RunMetric.Result(CorpusStage.AGGREGATOR, CorpusVerdict.FAIL);
        var drop = new RunMetric.Result(CorpusStage.QUALITY, CorpusVerdict.FAIL);
        assertEquals(RunPalette.Tone.NORMAL, RunPalette.tone(fail, new RunValue.Count(0)));
        assertEquals(RunPalette.Tone.WARNING, RunPalette.tone(fail, new RunValue.Count(1)));
        assertEquals(RunPalette.Tone.BAD, RunPalette.tone(differ, new RunValue.Count(1)));
        assertEquals(RunPalette.Tone.NORMAL, RunPalette.tone(drop, new RunValue.Count(1)));
    }
}
