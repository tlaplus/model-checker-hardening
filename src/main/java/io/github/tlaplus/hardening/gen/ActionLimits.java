package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * Bounds on action definitions and next-state disjuncts, independent of expression limits.
 *
 * @param maximumActionOperators maximum action definitions (each has a nonempty state effect)
 * @param maximumActions maximum next-state disjuncts
 * @param maximumActionParameters maximum bounded existential parameters per disjunct
 * @param maximumActionDepth maximum recursive shape depth; zero keeps shapes flat
 */
public record ActionLimits(int maximumActionOperators, int maximumActions,
                           int maximumActionParameters, int maximumActionDepth) {
    public static final int DEFAULT_MAXIMUM_ACTION_OPERATORS = 2;
    public static final int DEFAULT_MAXIMUM_ACTIONS = 3;
    public static final int DEFAULT_MAXIMUM_ACTION_PARAMETERS = 2;
    public static final int DEFAULT_MAXIMUM_ACTION_DEPTH = 3;

    public ActionLimits {
        Preconditions.requireNonnegative(maximumActionOperators, "maximumActionOperators");
        Preconditions.requirePositive(maximumActions, "maximumActions");
        Preconditions.requireNonnegative(maximumActionParameters, "maximumActionParameters");
        Preconditions.requireNonnegative(maximumActionDepth, "maximumActionDepth");
    }

    public static ActionLimits defaults() {
        return new ActionLimits(DEFAULT_MAXIMUM_ACTION_OPERATORS, DEFAULT_MAXIMUM_ACTIONS,
                DEFAULT_MAXIMUM_ACTION_PARAMETERS, DEFAULT_MAXIMUM_ACTION_DEPTH);
    }
}
