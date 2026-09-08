package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Objects;

/**
 * Declaration, action and exploration bounds on one module.
 *
 * @param maximumVariables maximum state variables, excluding the step counter
 * @param maximumAuxiliaryOperators maximum state-free definitions
 * @param actions bounds on action definitions and next-state disjuncts
 * @param maximumSteps transitions explored from an initial state
 */
public record ModuleLimits(int maximumVariables, int maximumAuxiliaryOperators,
                           ActionLimits actions, int maximumSteps) {
    public static final int DEFAULT_MAXIMUM_VARIABLES = 3;
    public static final int DEFAULT_MAXIMUM_AUXILIARY_OPERATORS = 2;
    public static final int DEFAULT_MAXIMUM_STEPS = 5;

    public ModuleLimits {
        Preconditions.requirePositive(maximumVariables, "maximumVariables");
        Preconditions.requireNonnegative(maximumAuxiliaryOperators, "maximumAuxiliaryOperators");
        Objects.requireNonNull(actions, "actions");
        Preconditions.requireNonnegative(maximumSteps, "maximumSteps");
    }

    public static ModuleLimits defaults() {
        return new ModuleLimits(DEFAULT_MAXIMUM_VARIABLES, DEFAULT_MAXIMUM_AUXILIARY_OPERATORS,
                ActionLimits.defaults(), DEFAULT_MAXIMUM_STEPS);
    }
}
