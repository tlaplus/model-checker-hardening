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
            assertEquals(AttributedStyle.DEFAULT.bold().inverse(), plain.style(RunPalette.Tone.BAD, true));
        }
        try (var fixture = RunTestTerminal.open("dumb")) {
            assertFalse(RunTerminal.supportsDisplay(fixture.terminal()));
            assertEquals(RunPalette.PLAIN, RunPalette.detect(fixture.terminal(), null));
        }
        assertEquals(AttributedStyle.BOLD, new RunPalette(false, false, true)
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
