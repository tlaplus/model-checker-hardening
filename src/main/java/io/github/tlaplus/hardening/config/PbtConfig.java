package io.github.tlaplus.hardening.config;

/** Limits for populating the initial property-based testing corpus. */
public record PbtConfig(int corpusEntries, int maximumInputBytes) {
    public PbtConfig {
        if (corpusEntries < 0) {
            throw new IllegalArgumentException("corpusEntries must be nonnegative");
        }
        if (maximumInputBytes < 0) {
            throw new IllegalArgumentException("maximumInputBytes must be nonnegative");
        }
        if (!hasEnoughDistinctInputs(corpusEntries, maximumInputBytes)) {
            throw new IllegalArgumentException(
                    "corpusEntries exceeds the number of distinct bounded inputs");
        }
    }

    /** Returns the limits written by {@code fuzztla init}. */
    public static PbtConfig defaults() {
        return new PbtConfig(1_000, 1_024);
    }

    /** Checks the finite input space without overflowing while calculating its cardinality. */
    private static boolean hasEnoughDistinctInputs(int target, int maximumLength) {
        long total = 1;
        long atLength = 1;
        for (var length = 1; length <= maximumLength && total < target; length++) {
            atLength = Math.min(target, atLength * 256);
            total = Math.min(target, total + atLength);
        }
        return total >= target;
    }
}
