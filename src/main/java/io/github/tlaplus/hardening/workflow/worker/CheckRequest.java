package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import java.util.Objects;
import java.util.Set;

/**
 * What a checker is asked about one assembled module.
 *
 * <p>Every module is checked against its invariant. A module that has a temporal property is also
 * checked against it: TLC through its configuration, Apalache through {@code --temporal}. A
 * metamorphic module (ADR 0016 §3) is also checked against its action invariant on every
 * transition. The parser reads none of these fields.
 *
 * <p>The control variables sequence the module's actions rather than compute data: the step
 * counter of every module, and {@code pc} of a graph module (ADR 0024 §1). TLC's exploration
 * metrics project them out of the states they count; no checker reads them otherwise.
 *
 * @param transitions the longest path the module's step counter admits from an initial state
 * @param temporalProperty whether the module's property is checked in addition to its invariant
 * @param actionInvariant whether the module's action invariant is checked on every transition
 * @param controlVariables the variables the exploration metrics project out
 */
public record CheckRequest(
        int transitions, boolean temporalProperty, boolean actionInvariant, Set<String> controlVariables) {
    /** The control variables of a module whose only control variable is its step counter. */
    public static final Set<String> STEP_ONLY = Set.of(GeneratedSpec.STEP_VARIABLE);

    public CheckRequest {
        Preconditions.requireNonnegative(transitions, "transitions");
        controlVariables = Set.copyOf(Objects.requireNonNull(controlVariables, "controlVariables"));
    }

    /** Returns a request whose only control variable is the step counter. */
    public CheckRequest(int transitions, boolean temporalProperty, boolean actionInvariant) {
        this(transitions, temporalProperty, actionInvariant, STEP_ONLY);
    }

    /** Returns a request without an action invariant, as for every conformance module. */
    public CheckRequest(int transitions, boolean temporalProperty) {
        this(transitions, temporalProperty, false);
    }

    /** Returns a request to check only the invariant over paths of the given length. */
    public static CheckRequest invariant(int transitions) {
        return new CheckRequest(transitions, false, false);
    }

    /** Returns this request with other control variables. */
    public CheckRequest withControlVariables(Set<String> replacement) {
        return new CheckRequest(transitions, temporalProperty, actionInvariant, replacement);
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
