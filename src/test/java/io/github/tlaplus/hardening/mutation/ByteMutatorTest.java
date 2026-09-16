package io.github.tlaplus.hardening.mutation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ByteMutatorTest {
    private static final byte[] PARENT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

    @Test
    void oneEditPerMutantWhenTheCapIsOne() {
        var mutator = new ByteMutator(defaults(), 1, 100);
        for (var seed = 0; seed < 100; seed++) {
            assertEquals(1, mutator.mutate(PARENT, () -> PARENT, new SplittableRandom(seed)).operators().size());
        }
    }

    @Test
    void stacksEditsUpToTheCap() {
        var mutator = new ByteMutator(defaults(), 4, 100);
        var counts = new HashSet<Integer>();
        for (var seed = 0; seed < 500; seed++) {
            var edits = mutator.mutate(PARENT, () -> PARENT, new SplittableRandom(seed)).operators().size();
            assertTrue(edits >= 1 && edits <= 4);
            counts.add(edits);
        }
        assertEquals(4, counts.size());
    }

    @Test
    void truncatesMutantsToTheMaximumLength() {
        var mutator = new ByteMutator(Map.of(MutationOperator.INSERT, 1), 4, PARENT.length);
        for (var seed = 0; seed < 100; seed++) {
            assertTrue(mutator.mutate(PARENT, () -> PARENT, new SplittableRandom(seed)).input().length
                    <= PARENT.length);
        }
    }

    @Test
    void choosesOnlyOperatorsWithPositiveWeight() {
        var mutator = new ByteMutator(
                Map.of(MutationOperator.BITFLIP, 1, MutationOperator.ERASE, 0), 1, 100);
        for (var seed = 0; seed < 100; seed++) {
            assertEquals(List.of(MutationOperator.BITFLIP),
                    mutator.mutate(PARENT, () -> PARENT, new SplittableRandom(seed)).operators());
        }
    }

    @Test
    void isDeterministicForASeed() {
        var mutator = new ByteMutator(defaults(), 3, 100);
        var first = mutator.mutate(PARENT, () -> PARENT, new SplittableRandom(42));
        var second = mutator.mutate(PARENT, () -> PARENT, new SplittableRandom(42));
        assertArrayEquals(first.input(), second.input());
        assertEquals(first.operators(), second.operators());
    }

    @Test
    void rejectsWeightsWithoutAPositiveSum() {
        assertThrows(IllegalArgumentException.class,
                () -> new ByteMutator(Map.of(MutationOperator.COPY, 0), 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ByteMutator(Map.of(MutationOperator.COPY, -1, MutationOperator.BITFLIP, 2), 1, 10));
    }

    private static Map<MutationOperator, Integer> defaults() {
        var weights = new EnumMap<MutationOperator, Integer>(MutationOperator.class);
        for (var operator : MutationOperator.values()) {
            weights.put(operator, operator.defaultWeight());
        }
        return weights;
    }
}
