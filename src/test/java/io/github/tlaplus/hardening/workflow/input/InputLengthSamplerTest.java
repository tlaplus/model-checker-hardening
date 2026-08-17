package io.github.tlaplus.hardening.workflow.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class InputLengthSamplerTest {
    @Test
    void zeroMaximumNeedsNoRandomChoice() {
        assertEquals(0, InputLengthSampler.sample(new StubRandom(), 0));
    }

    @Test
    void samplesUniformlyAddressableLogarithmicBuckets() {
        assertEquals(0, InputLengthSampler.sample(new StubRandom(0, 0), 8));
        assertEquals(3, InputLengthSampler.sample(new StubRandom(0, 3), 8));
        assertEquals(4, InputLengthSampler.sample(new StubRandom(1, 0), 8));
        assertEquals(7, InputLengthSampler.sample(new StubRandom(1, 3), 8));
        assertEquals(8, InputLengthSampler.sample(new StubRandom(2), 8));
    }

    @Test
    void truncatesTheFirstBucketAtTheConfiguredMaximum() {
        assertEquals(0, InputLengthSampler.sample(new StubRandom(0), 2));
        assertEquals(2, InputLengthSampler.sample(new StubRandom(2), 2));
    }

    @Test
    void rejectsNegativeMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> InputLengthSampler.sample(new StubRandom(), -1));
    }

    private static final class StubRandom implements RandomGenerator {
        private final ArrayDeque<Integer> values;

        StubRandom(Integer... values) {
            this.values = new ArrayDeque<>(Arrays.asList(values));
        }

        @Override
        public int nextInt(int bound) {
            int value = values.remove();
            if (value < 0 || value >= bound) {
                throw new AssertionError("stub value " + value + " is outside bound " + bound);
            }
            return value;
        }

        @Override
        public long nextLong() {
            return 0;
        }
    }
}
