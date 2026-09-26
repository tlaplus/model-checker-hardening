package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * What a checker is asked about one assembled module.
 *
 * <p>Every module is checked against its invariant. A module that has a temporal property is also
 * checked against it: TLC through its configuration, Apalache through {@code --temporal}. A
 * metamorphic module (ADR 0016 §3) is also checked against its action invariant on every
 * transition. The parser reads none of these fields.
 *
 * @param transitions the longest path the module's step counter admits from an initial state
 * @param temporalProperty whether the module's property is checked in addition to its invariant
 * @param actionInvariant whether the module's action invariant is checked on every transition
 */
public record CheckRequest(int transitions, boolean temporalProperty, boolean actionInvariant) {
    public CheckRequest {
        Preconditions.requireNonnegative(transitions, "transitions");
    }

    /** Returns a request without an action invariant, as for every conformance module. */
    public CheckRequest(int transitions, boolean temporalProperty) {
        this(transitions, temporalProperty, false);
    }

    /** Returns a request to check only the invariant over paths of the given length. */
    public static CheckRequest invariant(int transitions) {
        return new CheckRequest(transitions, false, false);
    }

    /**
     * Returns the number of transitions a bounded checker has to unroll.
     *
     * <p>An invariant needs the longest path. A temporal counterexample is a lasso, and every cycle
     * of a generated module is the stuttering step at the end of a path, so a lasso needs one
     * transition more: with fewer, Apalache misses a violation that TLC reports (ADR 0007).
     */
    public int unrollingLength() {
        return temporalProperty ? transitions + 1 : transitions;
    }
}
