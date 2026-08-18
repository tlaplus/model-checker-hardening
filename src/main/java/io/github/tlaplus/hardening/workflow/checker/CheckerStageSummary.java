package io.github.tlaplus.hardening.workflow.checker;

/** Verdict counters produced by one checker during a workflow run. */
public record CheckerStageSummary(long passed, long failed, long crashed) {
    public CheckerStageSummary {
        if (passed < 0 || failed < 0 || crashed < 0) {
            throw new IllegalArgumentException("checker counters must be nonnegative");
        }
    }

    public long processed() {
        return passed + failed + crashed;
    }
}
