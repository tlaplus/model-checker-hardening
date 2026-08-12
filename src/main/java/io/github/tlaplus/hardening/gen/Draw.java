package io.github.tlaplus.hardening.gen;

import java.util.List;
import java.util.Objects;

/**
 * A forward-only deterministic cursor that decodes structured choices from an unstructured byte
 * array.
 *
 * <p>A {@code Draw} owns a cursor shared by all generators participating in one generation. Every
 * operation consumes bytes from the current position, so composing generators also composes their
 * byte consumption. Replaying the same sequence of operations against the same bytes produces the
 * same values. The input array is retained rather than copied and therefore must not be mutated
 * while it is being consumed.
 *
 * <p>This model resembles Rust's {@code arbitrary::Arbitrary} and {@code Unstructured}, commonly
 * used with cargo-fuzz: both turn a fixed byte sequence into structured values by advancing a
 * shared cursor. It also resembles the function passed to a Hypothesis composite strategy: nested
 * generators request values through a common draw context instead of creating independent random
 * sources.
 *
 * <p>There are important differences. This class is not backed by a random source, a strategy
 * search engine, or a shrinker. It does not reject exhausted input: missing bytes decode to small,
 * useful defaults such as zero, {@code false}, or a range's lower bound. Variable-length values
 * use explicit Boolean continuation markers rather than decoded length fields. Consequently, this
 * is an implementation-local deterministic decoding protocol, not a compatibility layer for
 * either Arbitrary or Hypothesis.
 */
public final class Draw {
    private final byte[] input;
    private int cursor;

    /**
     * Creates a cursor at the beginning of {@code input}.
     *
     * <p>The array is not copied. Mutating it while this cursor is in use changes subsequent draws.
     *
     * @param input bytes to consume
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public Draw(byte[] input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    /**
     * Returns the number of bytes between the cursor and the end of the input. Synthetic zero
     * values returned after exhaustion do not affect this number.
     */
    public int remaining() {
        return input.length - cursor;
    }

    /** Returns whether the cursor has consumed every supplied byte. */
    public boolean isEmpty() {
        return cursor == input.length;
    }

    /**
     * Draws one byte as an unsigned integer in {@code [0, 255]}.
     *
     * <p>When input remains, this consumes exactly one byte. Once exhausted, it returns zero and
     * leaves the cursor at the end, allowing generators to finish with deterministic defaults.
     */
    public int drawByte() {
        return isEmpty() ? 0 : Byte.toUnsignedInt(input[cursor++]);
    }

    /**
     * Draws a Boolean from the low bit of one byte. Even bytes produce {@code false}; odd bytes
     * produce {@code true}. Exhaustion produces {@code false} without advancing.
     *
     * <p>The collection generators use this operation for continuation markers: an odd marker
     * introduces another optional element, while an even marker terminates the collection.
     */
    public boolean drawBoolean() {
        return (drawByte() & 1) != 0;
    }

    /**
     * Draws a {@code long} in the inclusive range {@code [minimum, maximum]}.
     *
     * <p>The operation consumes the minimum whole number of bytes needed to encode the range. It
     * reads those bytes in big-endian order, interprets their bit pattern as an unsigned value,
     * and reduces it modulo the number of values in the range. A singleton range consumes no
     * bytes. Missing bytes are zero, so complete exhaustion produces {@code minimum}. Since the
     * reduction uses modulo rather than rejection sampling, non-power-of-two ranges have modulo
     * bias when the input bytes themselves are uniformly distributed.
     *
     * <p>The fixed-width mapping is nevertheless optimally balanced for its discrete byte domain.
     * If the consumed bytes represent {@code D} possible values and the requested range contains
     * {@code R} values, every result receives either {@code floor(D / R)} or {@code ceil(D / R)}
     * inputs. Modulo assigns those inputs round-robin, so adjacent byte values usually produce
     * different results. Rejection sampling is intentionally avoided because its variable
     * consumption would make short mutation-fuzzer inputs more sensitive to preceding choices.
     *
     * <p>Range widths are calculated with unsigned {@code long} arithmetic. For the full interval
     * from {@link Long#MIN_VALUE} through {@link Long#MAX_VALUE}, the mathematical width is
     * {@code 2^64} and wraps to zero. Zero is treated as the full-width sentinel: eight bytes are
     * consumed, no remainder is taken, and adding their bit pattern to {@code minimum} maps every
     * possible 64-bit input to exactly one {@code long} result.
     *
     * @throws IllegalArgumentException if {@code minimum > maximum}
     */
    public long drawLong(long minimum, long maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        if (minimum == maximum) {
            return minimum;
        }
        // Compute the range. On [Long.MIN_VALUE, Long.MAX_VALUE], it overflows and becomes 0
        var range = maximum - minimum + 1;
        var byteCount = range == 0
                ? Long.BYTES
                : Math.max(
                        1,
                        (Long.SIZE - Long.numberOfLeadingZeros(range - 1) + Byte.SIZE - 1)
                                / Byte.SIZE);
        long value = 0;
        for (var index = 0; index < byteCount; index++) {
            value = (value << Byte.SIZE) | drawByte();
        }
        var offset = range == 0 ? value : Long.remainderUnsigned(value, range);
        return minimum + offset;
    }

    /**
     * Draws exactly {@code length} bytes in cursor order. If the input ends first, the remainder
     * of the returned array is padded with zero bytes.
     *
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public byte[] drawBytes(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be nonnegative");
        }
        var result = new byte[length];
        for (var index = 0; index < length; index++) {
            result[index] = (byte) drawByte();
        }
        return result;
    }

    /**
     * Chooses one value by drawing an index across the complete list. Selection uses the same
     * fixed-width, optimally balanced modulo mapping as {@link #drawLong(long, long)}. An exhausted
     * cursor selects the first value.
     *
     * @throws NullPointerException if {@code choices} is {@code null}
     * @throws IllegalArgumentException if {@code choices} is empty
     */
    public <T> T choose(List<? extends T> choices) {
        Objects.requireNonNull(choices, "choices");
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("choices must not be empty");
        }
        var index = Math.toIntExact(drawLong(0, choices.size() - 1L));
        return choices.get(index);
    }

    /**
     * Runs a nested generator against this cursor. No sub-cursor or independent byte source is
     * created, so the nested generator's consumption is visible to subsequent draws.
     *
     * @throws NullPointerException if {@code generator} is {@code null}
     */
    public <T> T draw(Generator<? extends T> generator) {
        return Objects.requireNonNull(generator, "generator").generate(this);
    }
}
