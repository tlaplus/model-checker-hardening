package io.github.tlaplus.hardening.config;

/** Limits for property-based input generation. */
public record PbtConfig(int maximumInputBytes) {
    public PbtConfig {
        if (maximumInputBytes < 0) {
            throw new IllegalArgumentException("maximumInputBytes must be nonnegative");
        }
    }

    /** Returns the limits written by {@code fuzztla init}. */
    public static PbtConfig defaults() {
        return new PbtConfig(1_024);
    }

    /** Checks the finite input space without overflowing while calculating its cardinality. */
    boolean supportsDistinctInputs(int target) {
        long total = 1;
        long atLength = 1;
        for (var length = 1; length <= maximumInputBytes && total < target; length++) {
            atLength = Math.min(target, atLength * 256);
            total = Math.min(target, total + atLength);
        }
        return total >= target;
    }
}
