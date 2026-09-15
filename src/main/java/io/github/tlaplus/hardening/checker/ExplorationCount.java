package io.github.tlaplus.hardening.checker;

import java.util.Optional;

/**
 * A counted exploration metric. The field name is part of the corpus format and of the worker
 * protocol; the declaration order is only the order in which metrics are written and reported.
 */
public enum ExplorationCount {
    /** Distinct initial states. */
    INIT_STATES("initStates"),
    /** Distinct reachable states, initial states included. */
    DISTINCT_STATES("distinctStates"),
    /** States generated, including duplicates. */
    GENERATED_STATES("generatedStates"),
    /** Distinct states after removing the step counter of a generated module. */
    PROJECTED_STATES("projectedStates"),
    /** Largest breadth-first level of a found state; an initial state is at depth 0. */
    DEPTH("depth"),
    /** Largest depth at which a new projected state appeared. */
    PROJECTED_DEPTH("projectedDepth"),
    /** Sub-actions the checker split the next-state action into. */
    ACTIONS("actions"),
    /** Disjuncts of the next-state action, by source location, that produced a transition. */
    ACTIONS_FIRED("actionsFired"),
    /** Disjuncts of the next-state action, by source location, that produced a new state. */
    ACTIONS_DISCOVERING("actionsDiscovering"),
    /** Largest value node count of a found state. */
    MAX_STATE_NODES("maxStateNodes"),
    /** Largest set, sequence, tuple, record or function domain in a found state. */
    MAX_CARDINALITY("maxCardinality"),
    /** Deepest value nesting in a found state; a scalar has nesting 0. */
    MAX_NESTING("maxNesting"),
    /** Transitions in the counterexample. Recorded only with the counterexample verdict. */
    TRACE_LENGTH("traceLength");

    private final String fieldName;

    ExplorationCount(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }

    /** Returns the count named {@code fieldName}, or empty for a name this build does not know. */
    public static Optional<ExplorationCount> fromFieldName(String fieldName) {
        for (var count : values()) {
            if (count.fieldName.equals(fieldName)) {
                return Optional.of(count);
            }
        }
        return Optional.empty();
    }
}
