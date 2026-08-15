package io.github.tlaplus.hardening.workflow;

/** Generation counters, admitted-input richness statistics, and replay information. */
public record PbtStageSummary(
        long seed,
        long existing,
        long added,
        long attempts,
        long rejected,
        long richnessRejected,
        long duplicates,
        double minimumRichness,
        double maximumRichness,
        double averageRichness) {
    public PbtStageSummary {
        if (seed < 0
                || existing < 0
                || added < 0
                || attempts < 0
                || rejected < 0
                || richnessRejected < 0
                || duplicates < 0) {
            throw new IllegalArgumentException("PBT counters must be nonnegative");
        }
        if (!Double.isFinite(minimumRichness)
                || !Double.isFinite(maximumRichness)
                || !Double.isFinite(averageRichness)
                || minimumRichness < 0.0
                || maximumRichness < 0.0
                || averageRichness < 0.0) {
            throw new IllegalArgumentException(
                    "PBT richness statistics must be finite and nonnegative");
        }
        if (added == 0) {
            if (minimumRichness != 0.0 || maximumRichness != 0.0 || averageRichness != 0.0) {
                throw new IllegalArgumentException(
                        "PBT richness statistics must be zero when no inputs were added");
            }
        } else if (minimumRichness > averageRichness || averageRichness > maximumRichness) {
            throw new IllegalArgumentException(
                    "average PBT richness must be between the minimum and maximum");
        }
    }
}
