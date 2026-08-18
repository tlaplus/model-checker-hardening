package io.github.tlaplus.hardening.workflow.checker;

import java.time.Duration;
import java.util.Objects;

/** Cumulative checker verdict counters and summed worker elapsed time. */
public record CheckerStageSummary(long passed, long failed, long crashed, Duration elapsed) {
    public CheckerStageSummary {
        if (passed < 0 || failed < 0 || crashed < 0) {
            throw new IllegalArgumentException("checker counters must be nonnegative");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("checker elapsed time must be nonnegative");
        }
    }

    public long processed() {
        return passed + failed + crashed;
    }
}
