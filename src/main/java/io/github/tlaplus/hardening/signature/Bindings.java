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
 * <p>An attempt to match an alternative at one expression starts from a fresh instance, and a failed
 * attempt discards its bindings. A descendant pattern tries several subexpressions in turn, so it
 * matches each against a {@link #copy()} and {@link #adopt(Bindings) adopts} the first that succeeds.
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

    /** Returns independent bindings with the same entries, for an attempt that may fail. */
    Bindings copy() {
        var result = new Bindings();
        result.expressions.putAll(expressions);
        result.types.putAll(types);
        return result;
    }

    /** Replaces these bindings with those of a successful attempt made on a {@link #copy()}. */
    void adopt(Bindings attempt) {
        expressions.clear();
        expressions.putAll(attempt.expressions);
        types.clear();
        types.putAll(attempt.types);
    }

    /** Binds a type variable, or checks an existing binding. */
    boolean bindType(VarT1 variable, TlaType1 type) {
        var bound = types.putIfAbsent(
                Objects.requireNonNull(variable, "variable"), Objects.requireNonNull(type, "type"));
        return bound == null || bound.equals(type);
    }
}
