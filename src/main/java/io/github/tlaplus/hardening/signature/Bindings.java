package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import at.forsyte.apalache.tla.lir.VarT1;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The metavariables and type variables one pattern alternative has bound so far.
 *
 * <p>Matching needs no backtracking, so a failed attempt simply discards its bindings: every
 * attempt to match an alternative at one expression starts from a fresh instance.
 */
final class Bindings {
    private final Map<String, TlaEx> expressions = new HashMap<>();
    private final Map<VarT1, TlaType1> types = new HashMap<>();

    /**
     * Binds a metavariable, or checks an existing binding. Expressions compare structurally, as
     * the IR defines equality; type tags do not take part.
     */
    boolean bindExpression(String name, TlaEx expression) {
        var bound = expressions.putIfAbsent(
                Objects.requireNonNull(name, "name"), Objects.requireNonNull(expression, "expression"));
        return bound == null || bound.equals(expression);
    }

    /** Binds a type variable, or checks an existing binding. */
    boolean bindType(VarT1 variable, TlaType1 type) {
        var bound = types.putIfAbsent(
                Objects.requireNonNull(variable, "variable"), Objects.requireNonNull(type, "type"));
        return bound == null || bound.equals(type);
    }
}
