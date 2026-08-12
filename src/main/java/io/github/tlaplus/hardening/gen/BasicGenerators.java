package io.github.tlaplus.hardening.gen;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Factory methods for basic generators that compose through a shared {@link Draw}. */
public final class BasicGenerators {
    private BasicGenerators() {}

    /**
     * Returns a generator that consumes no bytes and always returns {@code value}.
     *
     * @param value value to return, possibly {@code null}
     * @param <T> generated value type
     * @return constant generator
     */
    public static <T> Generator<T> constant(T value) {
        return draw -> value;
    }

    /**
     * Returns a generator that delegates to {@link Draw#drawBoolean()}.
     *
     * @return Boolean generator
     */
    public static Generator<Boolean> oneBoolean() {
        return Draw::drawBoolean;
    }

    /**
     * Returns a generator of values in the inclusive range {@code [minimum, maximum]}.
     *
     * @param minimum smallest permitted result
     * @param maximum largest permitted result
     * @return bounded {@code long} generator
     * @throws IllegalArgumentException if {@code minimum > maximum}
     */
    public static Generator<Long> oneLong(long minimum, long maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        return draw -> draw.drawLong(minimum, maximum);
    }

    /**
     * Returns a generator that selects one alternative and runs it against the same cursor.
     * Selection is deferred: this method only validates and snapshots {@code alternatives}; it does
     * not select or run an alternative until the returned generator is invoked. Each invocation
     * makes a new selection from that invocation's cursor. The eager snapshot prevents caller
     * mutations between generator construction and its later invocation from changing the available
     * alternatives.
     *
     * @param alternatives nonempty generators to choose from; copied by this method
     * @param <T> generated value type
     * @return generator selecting among {@code alternatives}
     * @throws NullPointerException if the list or one of its elements is {@code null}
     * @throws IllegalArgumentException if the list is empty
     */
    public static <T> Generator<T> oneOf(List<? extends Generator<? extends T>> alternatives) {
        Objects.requireNonNull(alternatives, "alternatives");
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("alternatives must not be empty");
        }
        var copy = List.copyOf(alternatives);
        return draw -> draw.draw(draw.choose(copy));
    }

    /**
     * Returns a generator that produces a list without decoding its size. Generation is deferred:
     * this method only validates {@code elements} and the size bounds. Each invocation allocates a
     * new mutable list and generates its elements from that invocation's cursor. The mutable list is
     * confined to that invocation and is snapshotted as an immutable result before it is returned;
     * no result list is allocated or copied when this factory method is called.
     *
     * <p>Mandatory elements are generated first; each optional element is preceded by a Boolean
     * continuation marker. An odd marker continues and an even marker stops; reaching
     * {@code maximumSize} consumes no additional marker.
     *
     * @param elements generator used for every element
     * @param minimumSize number of elements generated without continuation markers
     * @param maximumSize maximum number of generated elements
     * @param <T> element type
     * @return generator of immutable lists
     * @throws NullPointerException if {@code elements} is {@code null}
     * @throws IllegalArgumentException unless {@code 0 <= minimumSize <= maximumSize}
     */
    public static <T> Generator<List<T>> listOf(
            Generator<? extends T> elements, int minimumSize, int maximumSize) {
        Objects.requireNonNull(elements, "elements");
        if (minimumSize < 0 || minimumSize > maximumSize) {
            throw new IllegalArgumentException(
                    "expected 0 <= minimumSize <= maximumSize");
        }

        return draw -> {
            var result = new ArrayList<T>(minimumSize);
            for (var index = 0; index < minimumSize; index++) {
                result.add(draw.draw(elements));
            }
            while (result.size() < maximumSize && draw.drawBoolean()) {
                result.add(draw.draw(elements));
            }
            return List.copyOf(result);
        };
    }

    /**
     * Returns a generator that produces a byte array without decoding its size. Generation is
     * deferred until the returned generator is invoked, and every invocation returns a fresh,
     * caller-owned array.
     *
     * <p>Mandatory bytes are drawn first; each optional byte is preceded by a Boolean continuation
     * marker. An odd marker continues and an even marker stops; reaching {@code maximumSize}
     * consumes no additional marker. Exhausted mandatory draws produce zero bytes according to
     * {@link Draw#drawByte()}.
     *
     * @param minimumSize number of bytes drawn without continuation markers
     * @param maximumSize maximum number of bytes in the result
     * @return generator of mutable byte arrays
     * @throws IllegalArgumentException unless {@code 0 <= minimumSize <= maximumSize}
     */
    public static Generator<byte[]> byteArray(int minimumSize, int maximumSize) {
        if (minimumSize < 0 || minimumSize > maximumSize) {
            throw new IllegalArgumentException(
                    "expected 0 <= minimumSize <= maximumSize");
        }

        return draw -> {
            var result = new ByteArrayOutputStream(minimumSize);
            for (var index = 0; index < minimumSize; index++) {
                result.write(draw.drawByte());
            }
            while (result.size() < maximumSize && draw.drawBoolean()) {
                result.write(draw.drawByte());
            }
            return result.toByteArray();
        };
    }

    /**
     * Varargs form of {@link #oneOf(List)}. Selection and execution remain deferred until the
     * returned generator is invoked.
     *
     * @param alternatives nonempty generators to choose from
     * @param <T> generated value type
     * @return generator selecting among {@code alternatives}
     * @throws NullPointerException if the array or one of its elements is {@code null}
     * @throws IllegalArgumentException if the array is empty
     */
    @SafeVarargs
    public static <T> Generator<T> oneOf(Generator<? extends T>... alternatives) {
        return oneOf(List.of(alternatives));
    }
}
