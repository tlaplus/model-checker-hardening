package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Draw;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The slicing is part of every byte encoding that uses it. */
class WeightedSlicesTest {
    @Test
    void slicesAreContiguousProportionalAndOwnEveryByte() {
        var draw = new Draw(bytes(20));
        var slices = WeightedSlices.split(draw, List.of(1, 0, 3));
        assertEquals(List.of(5, 0, 15), slices.stream().map(Draw::remaining).toList());
        assertEquals(0, slices.get(0).drawByte());
        assertEquals(5, slices.get(2).drawByte());
        assertTrue(draw.isEmpty());
    }

    @Test
    void theLastWeightedPartTakesTheRemainder() {
        var slices = WeightedSlices.split(new Draw(bytes(7)), List.of(1, 1, 1, 0));
        assertEquals(List.of(2, 2, 3, 0), slices.stream().map(Draw::remaining).toList());
    }

    @Test
    void equalSlicesSplitEvenlyWithTheRemainderLast() {
        var slices = WeightedSlices.equal(new Draw(bytes(10)), 3);
        assertEquals(List.of(3, 3, 4), slices.stream().map(Draw::remaining).toList());
        assertEquals(List.of(), WeightedSlices.equal(new Draw(bytes(4)), 0));
    }

    @Test
    void anEmptyInputGivesEveryPartAnEmptyCursor() {
        var slices = WeightedSlices.split(new Draw(new byte[0]), List.of(2, 5));
        assertEquals(List.of(0, 0), slices.stream().map(Draw::remaining).toList());
    }

    @Test
    void rejectsANegativeWeight() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedSlices.split(new Draw(bytes(3)), List.of(1, -1)));
    }

    private static byte[] bytes(int length) {
        var bytes = new byte[length];
        for (var index = 0; index < length; index++) {
            bytes[index] = (byte) index;
        }
        return bytes;
    }
}
