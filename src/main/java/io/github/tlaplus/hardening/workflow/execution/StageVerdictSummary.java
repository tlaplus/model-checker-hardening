package io.github.tlaplus.hardening.workflow.execution;

import java.time.Duration;
import java.util.Objects;

/** Cumulative verdict counters and summed worker elapsed time for one stage. */
public record StageVerdictSummary(long passed, long failed, long crashed, Duration elapsed) {
    public StageVerdictSummary {
        if (passed < 0 || failed < 0 || crashed < 0) {
            throw new IllegalArgumentException("stage counters must be nonnegative");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("stage elapsed time must be nonnegative");
        }
    }

    /** Returns a summary with no processed inputs and no elapsed time. */
    public static StageVerdictSummary empty() {
        return new StageVerdictSummary(0, 0, 0, Duration.ZERO);
    }

    public long processed() {
        return passed + failed + crashed;
    }
}
