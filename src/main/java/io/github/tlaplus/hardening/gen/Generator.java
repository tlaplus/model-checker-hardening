package io.github.tlaplus.hardening.gen;

import java.util.Objects;
import java.util.function.Function;

/**
 * A composable recipe for decoding a value from a shared {@link Draw} cursor.
 *
 * <p>A generator describes how bytes are consumed; it does not own an input or a random source.
 * Calling {@link #generate(Draw)} runs that description at the cursor's current position and
 * leaves the cursor after the bytes it consumed. Nested generators therefore form one sequential
 * decoding process. A generator may leave an unused input suffix when it has finished constructing
 * its value.
 *
 * <p>Implementations should derive their result exclusively from the supplied cursor and immutable
 * configuration. Under that convention, the same generator and the same input bytes always
 * produce the same value. The interface cannot enforce purity: an implementation that reads
 * mutable external state, time, or randomness forfeits that determinism. Thread safety likewise
 * depends on the implementation and on never sharing one mutable {@code Draw} between threads.
 *
 * <p>This occupies a role similar to an implementation of Rust's {@code arbitrary::Arbitrary}: it
 * defines how to construct one structured value from unstructured bytes. Unlike that type-directed
 * trait, a {@code Generator} is a first-class value that can be selected and combined at runtime.
 * It is also similar to a Hypothesis strategy, especially when {@link #flatMap(Function)} makes a
 * later generator depend on an earlier result. Unlike Hypothesis strategies, generators have no
 * search engine, example database, distribution control, or shrinking protocol. They decode the
 * supplied bytes exactly once and propagate any exception raised by the decoding logic.
 *
 * <p>An implementation that reaches an expected semantic dead end may throw
 * {@link InputRejectedException}. That exception rejects only the current input. Programming
 * errors, violated invariants, and failures from builders should be allowed to propagate as
 * their original exceptions so fuzzing can expose them as defects.
 *
 * @param <T> type of value produced
 */
@FunctionalInterface
public interface Generator<T> {
    /**
     * Generates one value by consuming zero or more bytes from {@code draw} at its current
     * position.
     *
     * <p>The cursor is neither reset nor copied. Consumption performed by this generator remains
     * visible to its caller and to every generator run afterward. Implementations may obtain
     * deterministic exhaustion defaults from {@link Draw} when the supplied input is too short.
     *
     * @param draw cursor from which all choices should be made
     * @return generated value
     * @throws InputRejectedException if this input reaches an expected semantic dead end
     * @throws RuntimeException if the implementation cannot construct a value
     */
    T generate(Draw draw);

    /**
     * Generates one value from the beginning of {@code input} using a fresh cursor.
     *
     * <p>This is a convenience entry point for top-level generation. It does not require the
     * generator to consume the complete array, and it does not expose the final cursor position.
     * Use {@link #generate(Draw)} when subsequent decoding or consumption inspection is needed.
     * The array is retained by the cursor for the duration of the call rather than copied.
     *
     * @param input complete byte source for this generation
     * @return generated value
     * @throws NullPointerException if {@code input} is {@code null}
     * @throws InputRejectedException if this input reaches an expected semantic dead end
     * @throws RuntimeException if the implementation cannot construct a value
     */
    default T generate(byte[] input) {
        return generate(new Draw(input));
    }

    /**
     * Returns a generator that runs {@code this} generator and applies {@code transform} to the result.
     *
     * <p>The parameter {@code transform} receives no cursor and consumes no additional bytes unless it
     * does so through unrelated external state, which deterministic transformations should avoid.
     * Exceptions from either this generator or {@code transform} are propagated.
     *
     * @param transform function applied to this generator's result
     * @param <U> transformed result type
     * @return generator sharing this generator's byte-consumption behavior
     * @throws NullPointerException if {@code transform} is {@code null}
     */
    default <U> Generator<U> map(Function<? super T, ? extends U> transform) {
        Objects.requireNonNull(transform, "transform");
        return draw -> transform.apply(generate(draw));
    }

    /**
     * Returns a dependent generator that selects its next generator from {@code this} generator's result.
     *
     * <p>At generation time, {@code this} generator runs first. Its result is passed to {@code transform},
     * and the returned generator then runs against the same cursor at its new position. This makes
     * later structure depend on earlier decoded values without allocating a sub-input or resetting
     * byte consumption. It is analogous to a dependent draw in a Hypothesis composite strategy.
     * Exceptions from either generator or {@code transform} are propagated.
     *
     * @param transform function selecting the generator for the remaining computation
     * @param <U> final result type
     * @return generator that sequentially composes both decoding steps
     * @throws NullPointerException if {@code transform}, or the generator it returns, is
     *     {@code null}
     */
    default <U> Generator<U> flatMap(
            Function<? super T, ? extends Generator<? extends U>> transform) {
        Objects.requireNonNull(transform, "transform");
        return draw -> draw.draw(transform.apply(generate(draw)));
    }
}
