package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * Bounds on the declarations of one generated module.
 *
 * <p>These bound the shape of the module rather than the expressions inside it: how much state it
 * carries, how many definitions it may apply, how many alternatives its action offers, and how far
 * a checker explores it.
 *
 * @param maximumVariables maximum declared state variables, excluding the step counter
 * @param maximumAuxiliaryOperators maximum operator definitions the predicates may apply
 * @param maximumActions maximum disjuncts of the next-state action
 * @param maximumActionParameters maximum bounded existential parameters of one action
 * @param maximumSteps transitions explored from an initial state
 */
public record ModuleLimits(
        int maximumVariables,
        int maximumAuxiliaryOperators,
        int maximumActions,
        int maximumActionParameters,
        int maximumSteps) {

    public static final int DEFAULT_MAXIMUM_VARIABLES = 3;
    public static final int DEFAULT_MAXIMUM_AUXILIARY_OPERATORS = 2;
    public static final int DEFAULT_MAXIMUM_ACTIONS = 3;
    public static final int DEFAULT_MAXIMUM_ACTION_PARAMETERS = 2;
    public static final int DEFAULT_MAXIMUM_STEPS = 5;

    public ModuleLimits {
        Preconditions.requirePositive(maximumVariables, "maximumVariables");
        Preconditions.requireNonnegative(
                maximumAuxiliaryOperators, "maximumAuxiliaryOperators");
        Preconditions.requirePositive(maximumActions, "maximumActions");
        Preconditions.requireNonnegative(maximumActionParameters, "maximumActionParameters");
        Preconditions.requireNonnegative(maximumSteps, "maximumSteps");
    }

    public static ModuleLimits defaults() {
        return new ModuleLimits(
                DEFAULT_MAXIMUM_VARIABLES,
                DEFAULT_MAXIMUM_AUXILIARY_OPERATORS,
                DEFAULT_MAXIMUM_ACTIONS,
                DEFAULT_MAXIMUM_ACTION_PARAMETERS,
                DEFAULT_MAXIMUM_STEPS);
    }
}
