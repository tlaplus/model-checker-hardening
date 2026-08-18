package io.github.tlaplus.hardening.cli;

import java.time.Duration;
import java.util.Objects;

/** Formats nonnegative durations as compact day, hour, minute, and second parts. */
final class HumanDuration {
    private HumanDuration() {}

    static String format(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must be nonnegative");
        }
        var output = new StringBuilder();
        appendPart(output, duration.toDaysPart(), "d");
        appendPart(output, duration.toHoursPart(), "h");
        appendPart(output, duration.toMinutesPart(), "m");
        appendPart(output, duration.toSecondsPart(), "s");
        return output.isEmpty() ? "0s" : output.toString();
    }

    private static void appendPart(StringBuilder output, long value, String suffix) {
        if (value == 0) {
            return;
        }
        if (!output.isEmpty()) {
            output.append(' ');
        }
        output.append(value).append(suffix);
    }
}
