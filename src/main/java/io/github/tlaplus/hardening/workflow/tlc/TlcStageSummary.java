package io.github.tlaplus.hardening.workflow.tlc;

/** TLC verdict counters produced during one workflow run. */
public record TlcStageSummary(long passed, long failed, long crashed) {
    public TlcStageSummary {
        if (passed < 0 || failed < 0 || crashed < 0) {
            throw new IllegalArgumentException("TLC counters must be nonnegative");
        }
    }

    public long processed() {
        return passed + failed + crashed;
    }
}
