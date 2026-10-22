package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;

/**
 * Bounds on the control graph of one graph input (ADR 0023 §3). The data variables, the step
 * bound and the expressions of a graph module are bounded by {@link ModuleLimits} and
 * {@link ExpressionLimits}, as for a module input.
 *
 * @param maximumNodes maximum number of nodes; node 0 is initial
 * @param maximumSuccessors maximum number of distinct successors of one node
 */
@ArchRole(Role.CONFIG)
public record GraphLimits(int maximumNodes, int maximumSuccessors) {
    public static final int DEFAULT_MAXIMUM_NODES = 6;
    public static final int DEFAULT_MAXIMUM_SUCCESSORS = 3;

    public GraphLimits {
        Preconditions.requirePositive(maximumNodes, "maximumNodes");
        Preconditions.requireNonnegative(maximumSuccessors, "maximumSuccessors");
    }

    public static GraphLimits defaults() {
        return new GraphLimits(DEFAULT_MAXIMUM_NODES, DEFAULT_MAXIMUM_SUCCESSORS);
    }
}
