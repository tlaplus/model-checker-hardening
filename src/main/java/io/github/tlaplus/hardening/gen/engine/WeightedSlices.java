package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.gen.Draw;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Divides the remaining bytes of a cursor into contiguous slices of proportional length.
 *
 * <p>Each part with a positive weight receives the floor of its proportional share, and the last
 * such part also receives the remainder, so no byte is left unowned and the parent cursor ends
 * exhausted. A part of weight zero receives an empty cursor. The slicing is part of every byte
 * encoding that uses it, so it must not change.
 */
final class WeightedSlices {
    private WeightedSlices() {}

    /**
     * Returns one slice per weight, in order.
     *
     * @throws IllegalArgumentException if a weight is negative
     */
    static List<Draw> split(Draw draw, List<Integer> weights) {
        Objects.requireNonNull(draw, "draw");
        Objects.requireNonNull(weights, "weights");
        var total = 0L;
        var last = -1;
        for (var index = 0; index < weights.size(); index++) {
            var weight = weights.get(index);
            Preconditions.requireNonnegative(weight, "weight");
            if (weight > 0) {
                total += weight;
                last = index;
            }
        }
        var remaining = (long) draw.remaining();
        var slices = new ArrayList<Draw>(weights.size());
        for (var index = 0; index < weights.size(); index++) {
            final int length;
            if (weights.get(index) == 0) {
                length = 0;
            } else if (index == last) {
                length = draw.remaining();
            } else {
                length = Math.toIntExact(remaining * weights.get(index) / total);
            }
            slices.add(draw.slice(length));
        }
        return List.copyOf(slices);
    }

    /**
     * Returns {@code count} slices filled in order: each takes {@code max(minimum, n / count)} of
     * the {@code n} bytes of {@code draw}, or what is left, and the last takes the remainder. A
     * cursor shorter than {@code count * minimum} fills its first slices and leaves the later ones
     * empty, where {@link #equal} would give every byte to the last slice; a longer one is divided
     * as {@link #equal} divides it.
     */
    static List<Draw> filled(Draw draw, int count, int minimum) {
        Objects.requireNonNull(draw, "draw");
        Preconditions.requireNonnegative(count, "count");
        Preconditions.requirePositive(minimum, "minimum");
        var length = Math.max(minimum, draw.remaining() / Math.max(count, 1));
        var slices = new ArrayList<Draw>(count);
        for (var index = 0; index < count; index++) {
            slices.add(draw.slice(index == count - 1 ? draw.remaining() : Math.min(length, draw.remaining())));
        }
        return List.copyOf(slices);
    }

    /** Returns {@code count} slices of equal weight. */
    static List<Draw> equal(Draw draw, int count) {
        Preconditions.requireNonnegative(count, "count");
        return split(draw, java.util.Collections.nCopies(count, 1));
    }
}
