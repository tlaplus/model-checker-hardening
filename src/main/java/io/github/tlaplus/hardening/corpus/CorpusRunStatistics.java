package io.github.tlaplus.hardening.corpus;

/** Durable cumulative statistics that cannot be recovered cheaply from corpus entries. */
public record CorpusRunStatistics(
        long totalElapsedNanos,
        long generatorElapsedNanos,
        long parserElapsedNanos,
        long tlcElapsedNanos,
        long apalacheElapsedNanos,
        long generatorAttempts,
        long generatorRejected,
        long generatorRichnessRejected,
        long generatorDuplicates,
        long richnessSamples,
        double minimumRichness,
        double maximumRichness,
        double averageRichness) {
    public CorpusRunStatistics {
        if (totalElapsedNanos < 0
                || generatorElapsedNanos < 0
                || parserElapsedNanos < 0
                || tlcElapsedNanos < 0
                || apalacheElapsedNanos < 0
                || generatorAttempts < 0
                || generatorRejected < 0
                || generatorRichnessRejected < 0
                || generatorDuplicates < 0
                || richnessSamples < 0) {
            throw new IllegalArgumentException("workflow statistics must be nonnegative");
        }
        if (!Double.isFinite(minimumRichness)
                || !Double.isFinite(maximumRichness)
                || !Double.isFinite(averageRichness)
                || minimumRichness < 0.0
                || maximumRichness < 0.0
                || averageRichness < 0.0) {
            throw new IllegalArgumentException(
                    "richness statistics must be finite and nonnegative");
        }
        if (richnessSamples == 0) {
            if (minimumRichness != 0.0 || maximumRichness != 0.0 || averageRichness != 0.0) {
                throw new IllegalArgumentException(
                        "richness statistics must be zero without samples");
            }
        } else if (minimumRichness > averageRichness || averageRichness > maximumRichness) {
            throw new IllegalArgumentException(
                    "average richness must be between the minimum and maximum");
        }
    }

    /** Returns the initial statistics for a corpus that has not completed a tracked run. */
    public static CorpusRunStatistics empty() {
        return new CorpusRunStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
    }
}
