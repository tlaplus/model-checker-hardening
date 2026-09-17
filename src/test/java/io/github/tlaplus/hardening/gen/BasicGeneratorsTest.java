package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BasicGeneratorsTest {
    private static final CollectionLimits LIMITS = new CollectionLimits(16, 5, 3, 64);

    @Test
    void exhaustedInputYieldsTheBaseSize() {
        var draw = new Draw(new byte[0]);

        assertEquals(5, draw.draw(BasicGenerators.collectionSize(LIMITS, 0, 16)));
    }

    @Test
    void theIndexesCoverBasePlusOrMinusSpreadOnceEach() {
        var sizes = new ArrayList<Integer>();
        for (var index = 0; index < 7; index++) {
            sizes.add(size(LIMITS, index, 0, 16));
        }

        assertEquals(List.of(5, 6, 7, 8, 2, 3, 4), sizes);
    }

    @Test
    void aLowBitFlipMovesTheSizeByOneExceptAtTheWrap() {
        for (var index = 0; index < 6; index++) {
            var difference = Math.abs(size(LIMITS, index, 0, 16) - size(LIMITS, index + 1, 0, 16));
            assertTrue(index == 3 ? difference == 6 : difference == 1, "index " + index);
        }
    }

    @Test
    void theSizeIsClampedToTheRequestedBounds() {
        assertEquals(4, size(LIMITS, 4, 4, 16));
        assertEquals(6, size(LIMITS, 3, 0, 6));
        assertEquals(1, size(LIMITS.withBaseSize(0), 0, 1, 16));
    }

    @Test
    void aZeroSpreadStillConsumesOneByte() {
        var draw = new Draw(new byte[] {(byte) 0xff, 9});

        assertEquals(5, draw.draw(BasicGenerators.collectionSize(new CollectionLimits(16, 5, 0, 64), 0, 16)));
        assertEquals(1, draw.remaining());
    }

    @Test
    void rejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> BasicGenerators.collectionSize(LIMITS, 3, 2));
    }

    private static int size(CollectionLimits limits, int index, int minimum, int maximum) {
        return new Draw(new byte[] {(byte) index}).draw(BasicGenerators.collectionSize(limits, minimum, maximum));
    }
}
