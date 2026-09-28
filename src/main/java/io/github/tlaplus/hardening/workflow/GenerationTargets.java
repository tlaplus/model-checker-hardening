package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.EntryOrigin;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Which target ordinals of one generation are already spoken for, and which source fills the rest
 * (ADR 0010, ADR 0016 §6).
 *
 * <p>A generation of {@code size} entries numbers its targets {@code 0..size-1} and splits them into
 * one contiguous range per {@link EntryOrigin}, in declaration order: mutants, adopted entries, then
 * PBT. The split matters because a target's ordinal seeds its candidate stream: two targets that
 * share an ordinal replay one stream, so the ranges must never overlap, whichever source ends up
 * filling them.
 *
 * <p>Two cases make the split less obvious than the shares alone. When a source has no parents, PBT
 * fills its range — it still consumes that range's ordinals, not its own. And on resume a range can
 * only grow: a corpus that already holds more entries of an origin than the current share calls
 * for keeps them all in its range, a share yields to what later ranges already hold, and PBT
 * entries beyond the PBT range are taken to have filled the latest earlier range first.
 *
 * <p>Not thread-safe: the coordinator thread owns one instance per open generation.
 */
final class GenerationTargets {
    private final Map<EntryOrigin, Long> start = new EnumMap<>(EntryOrigin.class);
    private final Map<EntryOrigin, Long> end = new EnumMap<>(EntryOrigin.class);
    private final Map<EntryOrigin, Long> reserved = new EnumMap<>(EntryOrigin.class);

    private GenerationTargets() {}

    /**
     * Returns how many of a generation's {@code size} entries are mutants. Generation 0 has no
     * parents to mutate, so it has none.
     */
    static long mutantShare(long size, int generation, double feedbackRatio) {
        return generation == 0 ? 0 : Math.round(feedbackRatio * size);
    }

    /** Returns how many of a generation's {@code size} entries are adopted from a base corpus. */
    static long adoptShare(long size, double adoptRatio) {
        return Math.round(adoptRatio * size);
    }

    /**
     * Returns the reservation of a generation of {@code size} entries that should hold {@code
     * shares} of each origin but PBT, and already holds {@code held} of each origin.
     *
     * <p>A generation may already hold more than {@code size} entries, or more of an origin than
     * its share, when {@code generation_size} or a ratio changed between runs. Its range is then
     * full and nothing more is reserved; the entries it holds stay as they are.
     */
    static GenerationTargets resuming(long size, Map<EntryOrigin, Long> shares, Map<EntryOrigin, Long> held) {
        Preconditions.requireNonnegative(size, "size");
        Objects.requireNonNull(shares, "shares");
        Objects.requireNonNull(held, "held");
        var targets = new GenerationTargets();
        var origins = EntryOrigin.values();
        var position = 0L;
        for (var index = 0; index < origins.length; index++) {
            var origin = origins[index];
            var share = origin == EntryOrigin.PBT ? size : shares.getOrDefault(origin, 0L);
            var count = held.getOrDefault(origin, 0L);
            Preconditions.requireNonnegative(share, "share of " + origin);
            Preconditions.requireNonnegative(count, "held " + origin);
            // A share yields to what a later range, but PBT, already holds; held entries never yield.
            var laterHeld = 0L;
            for (var later = index + 1; later < origins.length - 1; later++) {
                laterHeld += held.getOrDefault(origins[later], 0L);
            }
            var free = size - position;
            var length = Math.min(free, Math.max(count, Math.min(Math.max(share, count), free - laterHeld)));
            targets.start.put(origin, position);
            position += Math.max(0, length);
            targets.end.put(origin, position);
            targets.reserved.put(origin, Math.min(count, targets.capacity(origin)));
        }
        var spill = held.getOrDefault(EntryOrigin.PBT, 0L) - targets.reserved.get(EntryOrigin.PBT);
        for (var index = origins.length - 2; index >= 0 && spill > 0; index--) {
            var origin = origins[index];
            var taken = Math.min(spill, targets.remaining(origin));
            targets.reserved.merge(origin, taken, Long::sum);
            spill -= taken;
        }
        return targets;
    }

    /** Returns how many entries of this generation no source has been asked for yet. */
    long remaining() {
        var total = 0L;
        for (var origin : EntryOrigin.values()) {
            total += remaining(origin);
        }
        return total;
    }

    /** Returns how many entries of one origin's range are still unreserved. */
    long remaining(EntryOrigin origin) {
        return capacity(origin) - reserved.get(origin);
    }

    /**
     * Reserves {@code count} entries of one origin's range and returns the ordinal of the first. The
     * caller says which source fills them; the ordinals are the same either way, so a range that
     * falls back to PBT for want of parents still leaves the other ranges untouched.
     */
    long reserve(EntryOrigin origin, long count) {
        Preconditions.require(count > 0 && count <= remaining(origin), "count must be in the range 1..remaining");
        var first = start.get(origin) + reserved.get(origin);
        reserved.merge(origin, count, Long::sum);
        return first;
    }

    private long capacity(EntryOrigin origin) {
        return end.get(origin) - start.get(origin);
    }
}
