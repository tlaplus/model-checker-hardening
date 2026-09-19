package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generations, the quality gate's selection, and the mutator's share and operators (ADR 0010).
 *
 * @param generationSize the entries each generation admits
 * @param feedbackRatio the share of a generation admitted from the mutator
 * @param maximumEdits the cap on stacked edits per mutant
 * @param weights the weight of every operator; an operator of weight 0 is never applied
 * @param gate what the quality gate keeps of a generation
 */
public record MutatorConfig(
        int generationSize,
        double feedbackRatio,
        int maximumEdits,
        Map<MutationOperator, Integer> weights,
        QualityGateConfig gate) {
    public MutatorConfig {
        Preconditions.requirePositive(generationSize, "generationSize");
        Preconditions.require(feedbackRatio >= 0.0 && feedbackRatio <= 1.0,
                "feedbackRatio must be in the range [0, 1]");
        Preconditions.requirePositive(maximumEdits, "maximumEdits");
        var weightCopy = new EnumMap<MutationOperator, Integer>(MutationOperator.class);
        for (var operator : MutationOperator.values()) {
            var weight = Objects.requireNonNull(weights, "weights").getOrDefault(operator, 0);
            Preconditions.requireNonnegative(weight, "weight of " + operator.encodedName());
            weightCopy.put(operator, weight);
        }
        Preconditions.require(weightCopy.values().stream().anyMatch(weight -> weight > 0),
                "mutation operator weights must not all be zero");
        weights = Collections.unmodifiableMap(weightCopy);
        Objects.requireNonNull(gate, "gate");
    }

    /** Returns the settings written by {@code fuzztla init}. */
    public static MutatorConfig defaults() {
        var weights = new EnumMap<MutationOperator, Integer>(MutationOperator.class);
        for (var operator : MutationOperator.values()) {
            weights.put(operator, operator.defaultWeight());
        }
        return new MutatorConfig(1_000, 0.5, 1, weights, QualityGateConfig.defaults());
    }
}
