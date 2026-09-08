package io.github.tlaplus.hardening.common;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.common.GeneratorAggregate.Richness;
import org.junit.jupiter.api.Test;

class GeneratorAggregateTest {
    @Test
    void rejectsEveryNegativeCounter() {
        for (int field = 0; field < 4; field++) {
            var counters = new long[4];
            counters[field] = -1;
            assertThrows(IllegalArgumentException.class, () -> new GeneratorAggregate(
                    counters[0], counters[1], counters[2], counters[3], Richness.empty()));
        }
        assertThrows(NullPointerException.class, () -> new GeneratorAggregate(0, 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new Richness(-1, 0, 0, 0));
    }

    @Test
    void rejectsNonfiniteOrNegativeRichnessInEveryPosition() {
        for (var invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (int field = 0; field < 3; field++) {
                var values = new double[] {1, 1, 1};
                values[field] = invalid;
                assertThrows(IllegalArgumentException.class,
                        () -> new Richness(1, values[0], values[1], values[2]));
            }
            assertThrows(IllegalArgumentException.class, () -> Richness.empty().include(invalid));
        }
    }

    @Test
    void validatesZeroSamplesAndMeanBounds() {
        assertEquals(new Richness(0, 0, 0, 0), Richness.empty());
        assertEquals(new GeneratorAggregate(0, 0, 0, 0, Richness.empty()), GeneratorAggregate.empty());
        for (int field = 0; field < 3; field++) {
            var values = new double[3];
            values[field] = 1;
            assertThrows(IllegalArgumentException.class,
                    () -> new Richness(0, values[0], values[1], values[2]));
        }
        assertThrows(IllegalArgumentException.class, () -> new Richness(2, 2, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> new Richness(2, 1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new Richness(2, 3, 1, 2));
    }

    @Test
    void accumulatesWithoutMutatingEarlierSnapshotsOrOverflowingTheMean() {
        var first = Richness.empty().include(2);
        var third = first.include(6).include(8);
        assertEquals(new Richness(1, 2, 2, 2), first);
        assertEquals(3, third.samples());
        assertEquals(2, third.minimum());
        assertEquals(8, third.maximum());
        assertEquals(16.0 / 3, third.average(), 1e-12);
        assertEquals(Double.MAX_VALUE / 2,
                Richness.empty().include(Double.MAX_VALUE).include(0).average());
        assertThrows(ArithmeticException.class,
                () -> new Richness(Long.MAX_VALUE, 1, 1, 1).include(1));
    }
}
