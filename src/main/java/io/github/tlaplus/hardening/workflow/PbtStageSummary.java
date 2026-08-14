package io.github.tlaplus.hardening.workflow;

/** Generation counters and replay information from one workflow run. */
public record PbtStageSummary(
        long seed,
        long existing,
        long added,
        long attempts,
        long rejected,
        long duplicates) {
    public PbtStageSummary {
        if (seed < 0
                || existing < 0
                || added < 0
                || attempts < 0
                || rejected < 0
                || duplicates < 0) {
            throw new IllegalArgumentException("PBT counters must be nonnegative");
        }
    }
}
