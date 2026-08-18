package io.github.tlaplus.hardening.workflow.parser;

import java.time.Duration;
import java.util.Objects;

/** Cumulative parser verdict counters and summed worker elapsed time. */
public record ParserStageSummary(long passed, long failed, long crashed, Duration elapsed) {
    public ParserStageSummary {
        if (passed < 0 || failed < 0 || crashed < 0) {
            throw new IllegalArgumentException("parser counters must be nonnegative");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("parser elapsed time must be nonnegative");
        }
    }

    public long processed() {
        return passed + failed + crashed;
    }
}
