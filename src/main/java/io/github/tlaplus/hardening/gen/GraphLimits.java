package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;

/**
 * Bounds on the control graph of one graph input (ADR 0023 §3). The data variables and the
 * expressions of a graph module are bounded by {@link ModuleLimits} and {@link ExpressionLimits},
 * as for a module input; its step bound is drawn per input and capped here.
 *
 * @param minimumNodes minimum number of nodes, and so of spine edges plus one
 * @param maximumNodes maximum number of nodes; node 0 is initial
 * @param maximumExtraSuccessors maximum successors of one node besides its spine successor
 * @param maximumSteps cap on the step bound an input draws
 */
@ArchRole(Role.CONFIG)
public record GraphLimits(int minimumNodes, int maximumNodes, int maximumExtraSuccessors, int maximumSteps) {
    public static final int DEFAULT_MINIMUM_NODES = 20;
    public static final int DEFAULT_MAXIMUM_NODES = 32;
    public static final int DEFAULT_MAXIMUM_EXTRA_SUCCESSORS = 2;
    public static final int DEFAULT_MAXIMUM_STEPS = DEFAULT_MAXIMUM_NODES;

    public GraphLimits {
        Preconditions.requirePositive(minimumNodes, "minimumNodes");
        Preconditions.require(minimumNodes <= maximumNodes, "minimumNodes must not exceed maximumNodes");
        Preconditions.requireNonnegative(maximumExtraSuccessors, "maximumExtraSuccessors");
        Preconditions.requireNonnegative(maximumSteps, "maximumSteps");
    }

    public static GraphLimits defaults() {
        return new GraphLimits(DEFAULT_MINIMUM_NODES, DEFAULT_MAXIMUM_NODES, DEFAULT_MAXIMUM_EXTRA_SUCCESSORS,
                DEFAULT_MAXIMUM_STEPS);
    }
}
