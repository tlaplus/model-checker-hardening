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

    /** Returns {@code count} slices of equal weight. */
    static List<Draw> equal(Draw draw, int count) {
        Preconditions.requireNonnegative(count, "count");
        return split(draw, java.util.Collections.nCopies(count, 1));
    }
}
