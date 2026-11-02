package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import java.util.Objects;
import java.util.Set;

/**
 * What a checker is asked about one assembled module.
 *
 * <p>{@code kind} says which side's specification an invocation explores and what it checks
 * there. The parser reads none of these fields.
 *
 * <p>The control variables sequence the module's actions rather than compute data: the step
 * counter of every module, and {@code pc} of a graph module (ADR 0024 §1). TLC's exploration
 * metrics project them out of the states they count; no checker reads them otherwise.
 *
 * @param transitions the longest path the module's step counter admits from an initial state
 * @param kind what the invocation checks
 * @param controlVariables the variables the exploration metrics project out
 */
public record CheckRequest(int transitions, CheckKind kind, Set<String> controlVariables) {
    /** The control variables of a module whose only control variable is its step counter. */
    public static final Set<String> STEP_ONLY = Set.of(GeneratedSpec.STEP_VARIABLE);

    public CheckRequest {
        Preconditions.requireNonnegative(transitions, "transitions");
        Objects.requireNonNull(kind, "kind");
        controlVariables = Set.copyOf(Objects.requireNonNull(controlVariables, "controlVariables"));
    }

    /** Returns a request whose only control variable is the step counter. */
    public CheckRequest(int transitions, CheckKind kind) {
        this(transitions, kind, STEP_ONLY);
    }

    /** Returns a request to check only the invariant over paths of the given length. */
    public static CheckRequest invariant(int transitions) {
        return new CheckRequest(transitions, CheckKind.INVARIANT);
    }

    /** Returns this request with other control variables. */
    public CheckRequest withControlVariables(Set<String> replacement) {
        return new CheckRequest(transitions, kind, replacement);
    }

    /** Returns this request for another invocation of the same module, such as a property run. */
    public CheckRequest withKind(CheckKind replacement) {
        return new CheckRequest(transitions, replacement, controlVariables);
    }

    /**
     * Returns the number of transitions a bounded checker has to unroll.
     *
     * <p>An invariant needs the longest path. A temporal counterexample is a lasso, and every cycle
     * of a generated module is the stuttering step at the end of a path, so a lasso needs one
     * transition more: with fewer, Apalache misses a violation that TLC reports (ADR 0007).
     */
    public int unrollingLength() {
        return kind.property() ? transitions + 1 : transitions;
    }
}
