package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DrawTest {
    @Test
    void exhaustedDrawsUseSmallDefaults() {
        var draw = new Draw(new byte[0]);

        assertFalse(draw.drawBoolean());
        assertEquals(0, draw.drawByte());
        assertEquals(-4, draw.drawLong(-4, 20));
        assertArrayEquals(new byte[] {0, 0}, draw.drawBytes(2));
        assertEquals("first", draw.choose(List.of("first", "second")));
        assertEquals(0, draw.remaining());
    }

    @Test
    void booleanUsesTheLowBit() {
        var draw = new Draw(new byte[] {2, 3});

        assertFalse(draw.drawBoolean());
        assertTrue(draw.drawBoolean());
        assertTrue(draw.isEmpty());
    }

    @Test
    void boundedValuesConsumeOnlyTheNecessaryBytes() {
        var draw = new Draw(new byte[] {(byte) 0xff, 7});

        assertEquals(5, draw.drawLong(5, 5));
        assertEquals(5, draw.drawLong(0, 9));
        assertEquals(1, draw.remaining());
    }

    @Test
    void fullLongRangeUsesAllEightBytesWithoutOverflow() {
        var draw = new Draw(new byte[] {
            (byte) 0xff,
            (byte) 0xff,
            (byte) 0xff,
            (byte) 0xff,
            (byte) 0xff,
            (byte) 0xff,
            (byte) 0xff,
            (byte) 0xff
        });

        assertEquals(Long.MAX_VALUE, draw.drawLong(Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(draw.isEmpty());
    }

    @Test
    void partiallyOverflowingLongRangesUseUnsignedWidths() {
        var bytes = new byte[] {
            (byte) 0x80, 0, 0, 0, 0, 0, 0, 0
        };

        assertEquals(Long.MAX_VALUE, new Draw(bytes).drawLong(-1, Long.MAX_VALUE));
        assertEquals(0, new Draw(bytes).drawLong(Long.MIN_VALUE, 0));
    }

    @Test
    void oneByteRangesAreOptimallyBalancedWithoutRejection() {
        for (var size : List.of(3, 10, 63, 127, 256)) {
            var counts = new int[size];
            for (var input = 0; input < 256; input++) {
                var draw = new Draw(new byte[] {(byte) input});
                counts[Math.toIntExact(draw.drawLong(0, size - 1L))]++;
                assertTrue(draw.isEmpty());
            }

            var floor = 256 / size;
            var ceiling = (256 + size - 1) / size;
            for (var count : counts) {
                assertTrue(count == floor || count == ceiling);
            }
        }
    }

    @Test
    void fixedReadsAreZeroPadded() {
        var draw = new Draw(new byte[] {1, 2});

        assertArrayEquals(new byte[] {1, 2, 0}, draw.drawBytes(3));
        assertTrue(draw.isEmpty());
    }

    @Test
    void collectionsUseContinuationMarkersInsteadOfSizes() {
        var draw = new Draw(new byte[] {1, 10, 1, 11, 0, 99});

        var values = BasicGenerators.listOf(Draw::drawByte, 0, 8).generate(draw);

        assertEquals(List.of(10, 11), values);
        assertEquals(1, draw.remaining());
    }

    @Test
    void mandatoryElementsDoNotNeedMarkers() {
        var draw = new Draw(new byte[0]);

        assertEquals(List.of(0), BasicGenerators.listOf(Draw::drawByte, 1, 4).generate(draw));
    }

    @Test
    void reachingTheCollectionCapDoesNotConsumeAnotherMarker() {
        var draw = new Draw(new byte[] {1, 10, 1, 11, 1, 12});

        assertEquals(
                List.of(10, 11), BasicGenerators.listOf(Draw::drawByte, 0, 2).generate(draw));
        assertEquals(2, draw.remaining());
    }

    @Test
    void payloadMutationsDoNotChangeCollectionBoundaries() {
        var first = BasicGenerators.listOf(Draw::drawByte, 0, 8)
                .generate(new byte[] {1, 10, 1, 11, 0});
        var mutated = BasicGenerators.listOf(Draw::drawByte, 0, 8)
                .generate(new byte[] {1, 42, 1, 11, 0});

        assertEquals(2, first.size());
        assertEquals(2, mutated.size());
        assertEquals(List.of(42, 11), mutated);
    }

    @Test
    void byteArraysUseTheCollectionContinuationProtocol() {
        var draw = new Draw(new byte[] {1, 10, 1, 11, 0, 99});

        var values = BasicGenerators.byteArray(0, 8).generate(draw);

        assertArrayEquals(new byte[] {10, 11}, values);
        assertEquals(1, draw.remaining());
    }

    @Test
    void mandatoryBytesDoNotNeedMarkers() {
        var draw = new Draw(new byte[0]);

        assertArrayEquals(new byte[] {0}, BasicGenerators.byteArray(1, 4).generate(draw));
    }

    @Test
    void reachingTheByteArrayCapDoesNotConsumeAnotherMarker() {
        var draw = new Draw(new byte[] {1, 10, 1, 11, 1, 12});

        assertArrayEquals(new byte[] {10, 11}, BasicGenerators.byteArray(0, 2).generate(draw));
        assertEquals(2, draw.remaining());
    }

    @Test
    void payloadMutationsDoNotChangeByteArrayBoundaries() {
        var first = BasicGenerators.byteArray(0, 8).generate(new byte[] {1, 10, 1, 11, 0});
        var mutated = BasicGenerators.byteArray(0, 8).generate(new byte[] {1, 42, 1, 11, 0});

        assertEquals(2, first.length);
        assertEquals(2, mutated.length);
        assertArrayEquals(new byte[] {42, 11}, mutated);
    }

    @Test
    void byteArrayResultsAreCallerOwned() {
        var generator = BasicGenerators.byteArray(1, 1);
        var first = generator.generate(new byte[] {10});
        var second = generator.generate(new byte[] {10});

        assertNotSame(first, second);
        first[0] = 42;
        assertArrayEquals(new byte[] {10}, second);
    }

    @Test
    void mapAndFlatMapShareTheCursor() {
        Generator<Long> first = BasicGenerators.oneLong(0, 9);
        var combined = first.flatMap(value -> BasicGenerators.oneLong(value, value + 9))
                .map(value -> value * 2);

        assertEquals(14, combined.generate(new byte[] {2, 5}));
    }

    @Test
    void compositionPreservesSemanticRejections() {
        var rejection = new InputRejectedException("unsatisfied constraint");
        Generator<Integer> rejecting = draw -> {
            throw rejection;
        };

        var thrown = assertThrows(
                InputRejectedException.class,
                () -> rejecting.map(value -> value + 1).generate(new byte[0]));

        assertSame(rejection, thrown);
    }

    @Test
    void invalidGeneratorArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Draw(new byte[0]).choose(List.of()));
        assertThrows(IllegalArgumentException.class, () -> BasicGenerators.oneOf(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> BasicGenerators.listOf(Draw::drawByte, 2, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> BasicGenerators.byteArray(-1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> BasicGenerators.byteArray(2, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Draw(new byte[0]).drawLong(1, 0));
    }
}
