package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusStage.ResultMeaning;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

/**
 * Capability-aware styles on the terminal's own palette and default background. Changes are marked
 * by bold text and a direction glyph, never by a background change.
 */
record RunPalette(boolean colors, boolean bold, Glyphs glyphs) {
    static final RunPalette PLAIN = new RunPalette(false, false, Glyphs.ASCII);

    enum Tone {
        NORMAL(-1, false),
        HEADING(AttributedStyle.CYAN, false),
        GOOD(AttributedStyle.GREEN, false),
        COUNTEREXAMPLE(AttributedStyle.MAGENTA, false),
        WARNING(AttributedStyle.YELLOW, true),
        BAD(AttributedStyle.RED, true);

        private final int color;
        private final boolean adverse;

        Tone(int color, boolean adverse) {
            this.color = color;
            this.adverse = adverse;
        }

        /** Exhaustive, so a new result meaning fails compilation until it has a tone. */
        static Tone of(ResultMeaning meaning) {
            return switch (meaning) {
                case PASS, AGREE, KEEP -> GOOD;
                case COUNTEREXAMPLE -> COUNTEREXAMPLE;
                case FAIL -> WARNING;
                case CRASH, DIFFER -> BAD;
                case DROP -> NORMAL;
            };
        }
    }

    static RunPalette detect(Terminal terminal, String noColor) {
        var count = terminal.getNumericCapability(Capability.max_colors);
        return new RunPalette(count != null && count >= 8 && (noColor == null || noColor.isEmpty()),
                terminal.getStringCapability(Capability.enter_bold_mode) != null,
                Glyphs.detect(terminal.encoding()));
    }

    AttributedStyle style(Tone tone, boolean changed) {
        var result = AttributedStyle.DEFAULT;
        if (colors && tone.color >= 0) {
            result = result.foreground(tone.color);
        }
        return bold && (tone == Tone.HEADING || changed) ? result.bold() : result;
    }

    static Tone tone(RunMetric metric, RunValue value) {
        if (metric instanceof RunMetric.Result result) {
            var tone = Tone.of(result.stage().meaning(result.verdict()));
            return tone.adverse && value instanceof RunValue.Count count && count.value() == 0
                    ? Tone.NORMAL : tone;
        }
        return metric == RunMetric.State.STATUS ? Tone.HEADING : Tone.NORMAL;
    }
}
