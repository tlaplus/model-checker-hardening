package io.github.tlaplus.hardening.mutation;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * One byte-level edit of a generator input, as ADR 0010 defines it.
 *
 * <p>The encoded name is part of the corpus format ({@code gen.operators}) and of the
 * configuration ({@code [mutator] weights}). Declaration order is only the order in which weights
 * are rendered and a weighted choice scans the operators.
 */
public enum MutationOperator {
    /** Replaces one byte at a uniformly chosen offset with a uniform byte. */
    RANDOM_BYTE("random_byte", 8, (input, donor, random) -> {
        var result = input.clone();
        result[random.nextInt(result.length)] = (byte) random.nextInt(256);
        return result;
    }),
    /** Flips one bit at a uniformly chosen offset. */
    BITFLIP("bitflip", 8, (input, donor, random) -> {
        var result = input.clone();
        result[random.nextInt(result.length)] ^= (byte) (1 << random.nextInt(Byte.SIZE));
        return result;
    }),
    /**
     * Flips the low bit of one byte. {@code Draw.drawBoolean} reads exactly that bit, so this toggles
     * a continuation marker when the byte is one.
     */
    PARITY_FLIP("parity_flip", 8, (input, donor, random) -> {
        var result = input.clone();
        result[random.nextInt(result.length)] ^= 1;
        return result;
    }),
    /** Copies a block of up to 32 bytes over another offset, keeping the length. */
    COPY("copy", 4, (input, donor, random) -> {
        var result = input.clone();
        var length = blockLength(input.length, Bounds.BLOCK, random);
        var source = random.nextInt(input.length - length + 1);
        var target = random.nextInt(input.length - length + 1);
        System.arraycopy(input, source, result, target, length);
        return result;
    }),
    /** Reinserts a block of up to 32 bytes directly after itself. */
    DUPLICATE("duplicate", 1, (input, donor, random) -> {
        var length = blockLength(input.length, Bounds.BLOCK, random);
        var start = random.nextInt(input.length - length + 1);
        var end = start + length;
        return concat(Arrays.copyOf(input, end), Arrays.copyOfRange(input, start, input.length));
    }),
    /** Inserts up to 8 uniform bytes at an offset. */
    INSERT("insert", 1, (input, donor, random) -> {
        var inserted = new byte[1 + random.nextInt(Bounds.INSERTION)];
        random.nextBytes(inserted);
        var offset = random.nextInt(input.length + 1);
        return concat(
                concat(Arrays.copyOf(input, offset), inserted),
                Arrays.copyOfRange(input, offset, input.length));
    }),
    /** Deletes up to 16 bytes at an offset. */
    ERASE("erase", 1, (input, donor, random) -> {
        var length = blockLength(input.length, Bounds.ERASURE, random);
        var offset = random.nextInt(input.length - length + 1);
        return concat(
                Arrays.copyOf(input, offset), Arrays.copyOfRange(input, offset + length, input.length));
    }),
    /** Concatenates a prefix of the input with a suffix of a second parent, cut independently. */
    SPLICE("splice", 1, (input, donor, random) -> {
        var other = Objects.requireNonNull(donor.get(), "donor");
        var prefix = random.nextInt(input.length + 1);
        var suffix = random.nextInt(other.length + 1);
        return concat(Arrays.copyOf(input, prefix), Arrays.copyOfRange(other, suffix, other.length));
    });

    /** Block sizes, held apart because enum constants cannot refer forward to their own fields. */
    private static final class Bounds {
        private static final int BLOCK = 32;
        private static final int INSERTION = 8;
        private static final int ERASURE = 16;
    }

    /** The edit of one operator on a nonempty input. */
    @FunctionalInterface
    private interface Edit {
        byte[] apply(byte[] input, Supplier<byte[]> donor, RandomGenerator random);
    }

    private final String encodedName;
    private final int defaultWeight;
    private final Edit edit;

    MutationOperator(String encodedName, int defaultWeight, Edit edit) {
        this.encodedName = encodedName;
        this.defaultWeight = defaultWeight;
        this.edit = edit;
    }

    /** Returns the name this operator is stored and configured under. */
    public String encodedName() {
        return encodedName;
    }

    /** Returns the weight {@code fuzztla init} writes for this operator. */
    public int defaultWeight() {
        return defaultWeight;
    }

    /**
     * Returns an edited copy of {@code input}. An empty input is returned unchanged, so the caller
     * rejects the result as a clone of its parent.
     *
     * @param donor supplies the second parent; only {@link #SPLICE} asks for it
     */
    public byte[] apply(byte[] input, Supplier<byte[]> donor, RandomGenerator random) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(donor, "donor");
        Objects.requireNonNull(random, "random");
        return input.length == 0 ? input.clone() : edit.apply(input, donor, random);
    }

    /** Returns the operator named {@code encodedName}, or empty for a name this build does not know. */
    public static Optional<MutationOperator> fromEncodedName(String encodedName) {
        return Arrays.stream(values())
                .filter(operator -> operator.encodedName.equals(encodedName))
                .findFirst();
    }

    private static int blockLength(int inputLength, int maximum, RandomGenerator random) {
        return 1 + random.nextInt(Math.min(maximum, inputLength));
    }

    private static byte[] concat(byte[] left, byte[] right) {
        var result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}
