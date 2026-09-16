package io.github.tlaplus.hardening.mutation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MutationOperatorTest {
    private static final Supplier<byte[]> NO_DONOR = () -> {
        throw new AssertionError("only splice asks for a donor");
    };

    @Test
    void encodedNamesArePartOfTheCorpusFormat() {
        assertEquals(
                List.of("random_byte", "bitflip", "parity_flip", "copy", "duplicate", "insert",
                        "erase", "splice"),
                Arrays.stream(MutationOperator.values()).map(MutationOperator::encodedName).toList());
        for (var operator : MutationOperator.values()) {
            assertEquals(Optional.of(operator), MutationOperator.fromEncodedName(operator.encodedName()));
        }
        assertEquals(Optional.empty(), MutationOperator.fromEncodedName("interesting"));
    }

    @Test
    void anEmptyInputIsReturnedUnchanged() {
        for (var operator : MutationOperator.values()) {
            assertArrayEquals(new byte[0], operator.apply(new byte[0], NO_DONOR, new SplittableRandom(1)));
        }
    }

    @Test
    void pointEditsChangeAtMostOneByteAndKeepTheLength() {
        for (var seed = 0; seed < 200; seed++) {
            var input = input(seed, 40);
            for (var operator : List.of(
                    MutationOperator.RANDOM_BYTE, MutationOperator.BITFLIP, MutationOperator.PARITY_FLIP)) {
                var result = operator.apply(input, NO_DONOR, new SplittableRandom(seed));
                assertEquals(input.length, result.length);
                assertTrue(differingBytes(input, result) <= 1);
            }
        }
    }

    @Test
    void bitAndParityFlipsChangeExactlyTheRequiredBits() {
        for (var seed = 0; seed < 200; seed++) {
            var input = input(seed, 40);
            var flipped = MutationOperator.BITFLIP.apply(input, NO_DONOR, new SplittableRandom(seed));
            var parity = MutationOperator.PARITY_FLIP.apply(input, NO_DONOR, new SplittableRandom(seed));
            assertEquals(1, differingBits(input, flipped));
            assertEquals(1, differingBits(input, parity));
            for (var index = 0; index < input.length; index++) {
                assertEquals(input[index] & ~1, parity[index] & ~1);
            }
        }
    }

    @Test
    void copyKeepsTheLengthAndDuplicateGrowsByOneBlock() {
        for (var seed = 0; seed < 200; seed++) {
            var input = input(seed, 50);
            var copied = MutationOperator.COPY.apply(input, NO_DONOR, new SplittableRandom(seed));
            assertEquals(input.length, copied.length);
            var duplicated = MutationOperator.DUPLICATE.apply(input, NO_DONOR, new SplittableRandom(seed));
            var growth = duplicated.length - input.length;
            assertTrue(growth >= 1 && growth <= 32, "growth " + growth);
        }
    }

    @Test
    void insertAndEraseStayWithinTheirBounds() {
        for (var seed = 0; seed < 200; seed++) {
            var input = input(seed, 50);
            var inserted = MutationOperator.INSERT.apply(input, NO_DONOR, new SplittableRandom(seed));
            var growth = inserted.length - input.length;
            assertTrue(growth >= 1 && growth <= 8, "growth " + growth);
            var erased = MutationOperator.ERASE.apply(input, NO_DONOR, new SplittableRandom(seed));
            var shrink = input.length - erased.length;
            assertTrue(shrink >= 1 && shrink <= 16, "shrink " + shrink);
        }
    }

    @Test
    void eraseNeverRemovesMoreThanTheInput() {
        for (var seed = 0; seed < 50; seed++) {
            var result = MutationOperator.ERASE.apply(new byte[] {7}, NO_DONOR, new SplittableRandom(seed));
            assertEquals(0, result.length);
        }
    }

    @Test
    void spliceJoinsAPrefixOfTheParentWithASuffixOfTheDonor() {
        var parent = new byte[] {1, 1, 1, 1, 1, 1};
        var donor = new byte[] {2, 2, 2, 2, 2, 2, 2};
        for (var seed = 0; seed < 100; seed++) {
            var result = MutationOperator.SPLICE.apply(parent, () -> donor, new SplittableRandom(seed));
            var prefix = 0;
            while (prefix < result.length && result[prefix] == 1) {
                prefix++;
            }
            for (var index = prefix; index < result.length; index++) {
                assertEquals(2, result[index]);
            }
            assertTrue(prefix <= parent.length && result.length - prefix <= donor.length);
        }
    }

    @Test
    void doesNotModifyItsArguments() {
        var input = input(3, 20);
        var original = input.clone();
        for (var operator : MutationOperator.values()) {
            operator.apply(input, () -> input(4, 20), new SplittableRandom(5));
            assertArrayEquals(original, input);
        }
    }

    private static byte[] input(long seed, int length) {
        var bytes = new byte[length];
        new SplittableRandom(seed ^ 0x5DEECE66DL).nextBytes(bytes);
        return bytes;
    }

    private static int differingBytes(byte[] left, byte[] right) {
        var count = 0;
        for (var index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                count++;
            }
        }
        return count;
    }

    private static int differingBits(byte[] left, byte[] right) {
        var count = 0;
        for (var index = 0; index < left.length; index++) {
            count += Integer.bitCount((left[index] ^ right[index]) & 0xFF);
        }
        return count;
    }
}
