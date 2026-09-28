package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.lir.TlaType1;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * One formal parameter of a rewrite rule, classified by where it occurs (ADR 0017 §3).
 *
 * @param type the type Snowcat inferred, which may hold type variables
 */
public record RuleParameter(String name, TlaType1 type, Kind kind) {
    /** How the rewriter obtains the parameter's value. */
    public enum Kind {
        /** Occurs in the pattern; a match binds it to a subterm of the rewritten node. */
        MATCHED,
        /** Occurs only in the replacement; the generator draws it at its type. */
        FRESH,
        /**
         * An operator parameter, applied in the pattern only to distinct variables the pattern
         * binds; a match binds it to a lambda.
         */
        HIGHER_ORDER
    }

    public RuleParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
    }

    /** Returns how many arguments a higher-order parameter takes; 0 for a value parameter. */
    public int arity() {
        return type instanceof OperT1 operator ? TlaTypes.operatorArguments(operator).size() : 0;
    }
}
