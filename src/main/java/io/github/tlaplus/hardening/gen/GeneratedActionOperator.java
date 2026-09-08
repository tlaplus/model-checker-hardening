package io.github.tlaplus.hardening.gen;

import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.util.Objects;

/**
 * One generated action operator: a definition the next-state action may apply.
 *
 * <p>Unlike an auxiliary operator, an action operator reads the current state and primes state
 * variables. Its body accounts for exactly the variables named in {@code effect}, once each, on
 * every execution path — by a primed assignment or an {@code UNCHANGED}. A next-state disjunct that
 * applies it therefore accounts for that effect set, which is what lets the completeness check
 * resolve an application by recursing into the applied body.
 *
 * @param declaration the {@code Act<N> == ...} definition, in dependency order
 * @param effect the names of the state variables the body accounts for
 */
public record GeneratedActionOperator(TlaOperDecl declaration, ActionEffect effect)
        implements GeneratedOperator {
    public GeneratedActionOperator {
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(effect, "effect");
    }
}
