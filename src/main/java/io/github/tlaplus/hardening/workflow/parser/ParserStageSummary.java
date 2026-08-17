package io.github.tlaplus.hardening.workflow.parser;

/** Parser verdict counters produced during one workflow run. */
public record ParserStageSummary(long passed, long failed, long crashed) {
    public ParserStageSummary {
        if (passed < 0 || failed < 0 || crashed < 0) {
            throw new IllegalArgumentException("parser counters must be nonnegative");
        }
    }

    public long processed() {
        return passed + failed + crashed;
    }
}
