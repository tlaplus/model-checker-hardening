package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/** Shared field formatting; attributed text keeps terminal escapes out of width arithmetic. */
record RunText(Map<RunMetric, RunValue> values, Precision precision,
        RunPalette palette, Highlights highlights) {
    static final String INPUTS_HEADING = "INPUTS";
    static final String COUNTEREXAMPLE_LEGEND =
            CorpusStage.ResultMeaning.COUNTEREXAMPLE.label() + ": counterexamples";

    /**
     * Recent changes by direction. A live screen reserves one column after each ordered value, so a
     * marker appearing or expiring never shifts the text after it; reports reserve none.
     */
    record Highlights(Map<RunMetric, RunValue.Direction> changes, boolean markers) {
        static final Highlights NONE = new Highlights(Map.of(), false);

        static Highlights live(Map<RunMetric, RunValue.Direction> changes) {
            return new Highlights(changes, true);
        }
    }

    Line line() {
        return new Line();
    }

    final class Line {
        private final AttributedStringBuilder text = new AttributedStringBuilder();

        Line text(String value) {
            text.append(value, AttributedStyle.DEFAULT);
            return this;
        }

        Line heading(String value) {
            text.append(value, palette.style(RunPalette.Tone.HEADING, false));
            return this;
        }

        Line stage(CorpusStage stage) {
            return heading(stage.displayName().toUpperCase(Locale.ROOT));
        }

        Line value(RunMetric key) {
            var value = Objects.requireNonNull(values.get(key), () -> "no value for " + key);
            var direction = highlights.changes().get(key);
            var style = palette.style(RunPalette.tone(key, value), direction != null);
            text.append(value.format(precision), style);
            if (highlights.markers() && value.directional()) {
                text.append(direction == null ? " " : palette.glyphs().marker(direction), style);
            }
            return this;
        }

        /** Appends a separator, the field's live-screen label, and its value. */
        Line field(String separator, RunMetric.Field key) {
            return text(separator + key.shortLabel() + " ").value(key);
        }

        Line queue(CorpusStage stage) {
            return text(RunMetric.Queue.LABEL + " ").value(new RunMetric.Queue(stage));
        }

        Line work(CorpusStage stage) {
            return text(RunMetric.StageElapsed.SHORT_LABEL + " ").value(new RunMetric.StageElapsed(stage));
        }

        Line result(CorpusStage stage, CorpusVerdict verdict) {
            var key = new RunMetric.Result(stage, verdict);
            text.append(stage.meaning(verdict).label() + " ",
                    palette.style(RunPalette.tone(key, values.get(key)), false));
            return value(key);
        }

        Line append(AttributedString value) {
            text.append(value);
            return this;
        }

        Line padTo(int column) {
            return text(" ".repeat(Math.max(0, column - text.columnLength())));
        }

        AttributedString build() {
            return text.toAttributedString();
        }
    }
}
