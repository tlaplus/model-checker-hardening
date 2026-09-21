package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * Which target ordinals of one generation are already spoken for, and which source fills the rest
 * (ADR 0010).
 *
 * <p>A generation of {@code size} entries numbers its targets {@code 0..size-1}. Mutants occupy the
 * prefix {@code [0, boundary)} and PBT the suffix {@code [boundary, size)}, where {@code boundary}
 * is the mutant share of the generation. The split matters because a target's ordinal seeds its
 * candidate stream: two targets that share an ordinal replay one stream, so the prefix and the
 * suffix must never overlap, whichever source ends up filling them.
 *
 * <p>Two cases make the split less obvious than the ratio alone. When no parent has passed the
 * quality gate, PBT fills the prefix too — it still consumes prefix ordinals, not suffix ones.
 * And on resume the boundary can only move outward: a corpus that already holds more mutants than
 * the current {@code feedback_ratio} calls for keeps them all in its prefix.
 *
 * <p>Not thread-safe: the coordinator thread owns one instance per open generation.
 */
final class GenerationTargets {
    private final long size;
    private final long boundary;
    private long prefixReserved;
    private long suffixReserved;

    private GenerationTargets(long size, long boundary, long prefixReserved, long suffixReserved) {
        this.size = size;
        this.boundary = boundary;
        this.prefixReserved = prefixReserved;
        this.suffixReserved = suffixReserved;
    }

    /**
     * Returns how many of a generation's {@code size} entries are mutants. Generation 0 has no
     * parents to mutate, so it is PBT throughout.
     */
    static long mutantShare(long size, int generation, double feedbackRatio) {
        return generation == 0 ? 0 : Math.round(feedbackRatio * size);
    }

    /**
     * Returns the reservation of a generation that has already admitted {@code admitted} entries,
     * {@code mutants} of them from the mutator. PBT fills the suffix first and spills into the
     * prefix only once the suffix is full, which is how an interrupted run that found no parents
     * left the range.
     *
     * <p>A generation may already hold more than {@code size} entries, or more mutants than
     * {@code mutantShare}, when {@code generation_size} or {@code feedback_ratio} changed between
     * runs. Its range is then full and nothing more is reserved; the entries it holds stay as they
     * are.
     */
    static GenerationTargets resuming(long size, long mutantShare, long admitted, long mutants) {
        Preconditions.requireNonnegative(size, "size");
        Preconditions.requireNonnegative(mutantShare, "mutantShare");
        Preconditions.requireNonnegative(admitted, "admitted");
        Preconditions.require(mutants >= 0 && mutants <= admitted,
                "mutants must be in the range 0..admitted");
        var boundary = Math.min(size, Math.max(mutantShare, mutants));
        var pbt = admitted - mutants;
        var suffix = Math.min(pbt, size - boundary);
        var prefix = Math.min(boundary, mutants + (pbt - suffix));
        return new GenerationTargets(size, boundary, prefix, suffix);
    }

    /** Returns how many entries of this generation no source has been asked for yet. */
    long remaining() {
        return size - prefixReserved - suffixReserved;
    }

    /** Returns how many prefix entries — the mutant share — are still unreserved. */
    long prefixRemaining() {
        return boundary - prefixReserved;
    }

    /** Returns how many suffix entries — the PBT share — are still unreserved. */
    long suffixRemaining() {
        return size - boundary - suffixReserved;
    }

    /**
     * Reserves {@code count} prefix entries and returns the ordinal of the first. The caller says
     * which source fills them; the ordinals are the same either way, so a generation that falls
     * back to PBT for want of parents still leaves the suffix untouched.
     */
    long reservePrefix(long count) {
        Preconditions.require(count > 0 && count <= prefixRemaining(),
                "count must be in the range 1..prefixRemaining");
        var first = prefixReserved;
        prefixReserved += count;
        return first;
    }

    /** Reserves {@code count} suffix entries and returns the ordinal of the first. */
    long reserveSuffix(long count) {
        Preconditions.require(count > 0 && count <= suffixRemaining(),
                "count must be in the range 1..suffixRemaining");
        var first = boundary + suffixReserved;
        suffixReserved += count;
        return first;
    }
}
