package io.github.tlaplus.hardening.pbt;

/** Counters and replay information from one PBT corpus-generation run. */
public record PbtRunSummary(
        long seed,
        long existing,
        long added,
        long attempts,
        long rejected,
        long duplicates) {
    public PbtRunSummary {
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be nonnegative");
        }
        if (existing < 0 || added < 0 || attempts < 0 || rejected < 0 || duplicates < 0) {
            throw new IllegalArgumentException("run counters must be nonnegative");
        }
    }
}
