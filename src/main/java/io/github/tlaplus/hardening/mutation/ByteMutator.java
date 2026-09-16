package io.github.tlaplus.hardening.mutation;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Derives one mutant from a parent by stacking weighted operator edits.
 *
 * <p>A mutant receives {@code 1 + G} edits, where {@code G} is geometric with parameter one half,
 * capped at {@code maxEdits}. Each intermediate result is truncated to {@code maxLength}, so growing
 * operators cannot exceed the input bound.
 */
public final class ByteMutator {
    /** A mutated input and the operators applied to its parent, in order. */
    public record Mutant(byte[] input, List<MutationOperator> operators) {
        public Mutant {
            input = Objects.requireNonNull(input, "input").clone();
            operators = List.copyOf(operators);
            Preconditions.require(!operators.isEmpty(), "a mutant has at least one operator");
        }

        @Override
        public byte[] input() {
            return input.clone();
        }
    }

    private final Map<MutationOperator, Integer> weights;
    private final int totalWeight;
    private final int maxEdits;
    private final int maxLength;

    public ByteMutator(Map<MutationOperator, Integer> weights, int maxEdits, int maxLength) {
        Objects.requireNonNull(weights, "weights");
        Preconditions.requirePositive(maxEdits, "maxEdits");
        Preconditions.requireNonnegative(maxLength, "maxLength");
        var copy = new EnumMap<MutationOperator, Integer>(MutationOperator.class);
        weights.forEach((operator, weight) -> {
            Preconditions.requireNonnegative(weight, "weight of " + operator.encodedName());
            copy.put(operator, weight);
        });
        this.weights = Collections.unmodifiableMap(copy);
        totalWeight = copy.values().stream().mapToInt(Integer::intValue).sum();
        Preconditions.require(totalWeight > 0, "operator weights must have a positive sum");
        this.maxEdits = maxEdits;
        this.maxLength = maxLength;
    }

    /**
     * Mutates {@code parent}.
     *
     * @param donor supplies a second parent when a splice is drawn
     */
    public Mutant mutate(byte[] parent, Supplier<byte[]> donor, RandomGenerator random) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(donor, "donor");
        Objects.requireNonNull(random, "random");
        var edits = 1;
        while (edits < maxEdits && random.nextBoolean()) {
            edits++;
        }
        var input = parent;
        var operators = new ArrayList<MutationOperator>(edits);
        for (var edit = 0; edit < edits; edit++) {
            var operator = choose(random);
            input = operator.apply(input, donor, random);
            if (input.length > maxLength) {
                input = Arrays.copyOf(input, maxLength);
            }
            operators.add(operator);
        }
        return new Mutant(input, operators);
    }

    private MutationOperator choose(RandomGenerator random) {
        var remaining = random.nextInt(totalWeight);
        for (var entry : weights.entrySet()) {
            remaining -= entry.getValue();
            if (remaining < 0) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("weighted choice exceeded the total weight");
    }
}
