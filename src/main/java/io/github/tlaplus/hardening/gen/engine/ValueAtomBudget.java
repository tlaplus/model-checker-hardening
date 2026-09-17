package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.Objects;

/**
 * Bounds the elements of one generated collection value over all nesting levels (ADR 0011).
 *
 * <p>A collection of {@code n} elements generated under budget {@code a} gives each element the
 * budget {@code floor(a / n)}, so nested base sizes cannot multiply beyond the configured maximum.
 * The budget costs no bytes. Like the node budget, a scope restores the prior value on exit,
 * including an exceptional one.
 */
final class ValueAtomBudget {
    private final int maximum;
    private int current;

    ValueAtomBudget(int maximum) {
        Preconditions.requirePositive(maximum, "maximum");
        this.maximum = maximum;
        this.current = maximum;
    }

    /** Returns the budget of a collection generated now; always at least one. */
    int current() {
        return current;
    }

    /** Returns a generator that runs its body with the budget of one of {@code elements} elements. */
    <T> Generator<T> within(int elements, Generator<? extends T> body) {
        Preconditions.requirePositive(elements, "elements");
        Objects.requireNonNull(body, "body");
        return draw -> {
            var previous = current;
            current = Math.max(1, previous / elements);
            try {
                return draw.draw(body);
            } finally {
                current = previous;
            }
        };
    }

    /** Returns a generator that runs its body with the full budget, as a top-level body does. */
    <T> Generator<T> fresh(Generator<? extends T> body) {
        Objects.requireNonNull(body, "body");
        return draw -> {
            var previous = current;
            current = maximum;
            try {
                return draw.draw(body);
            } finally {
                current = previous;
            }
        };
    }
}
