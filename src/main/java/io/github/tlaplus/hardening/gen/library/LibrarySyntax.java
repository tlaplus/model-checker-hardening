package io.github.tlaplus.hardening.gen.library;

import static io.github.tlaplus.hardening.gen.ExpressionCategory.*;
import static org.apalache_mc.tla.jir.TlaOperators.*;

import at.forsyte.apalache.tla.lir.oper.TlaOper;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import org.apalache_mc.tla.jir.TlaOperators;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Supported imported IR syntax and its capabilities; {@code IrBinding} gives its binding layout. */
enum LibrarySyntax {
    LOGIC(Set.of(BOOL_LOGIC), EQ, NE, AND, OR, NOT, IMPLIES, EQUIV),
    MATH(Set.of(ARITHMETIC),
            PLUS, UNARY_MINUS, MINUS, MULT, DIV, MOD, POW, LT, GT, LE, GE),
    SETS(Set.of(SET),
            SET_ENUM, SET_IN, SET_NOT_IN, SET_UNION2, SET_INTERSECT, SET_SUBSET_EQ,
            SET_MINUS, SET_POWERSET, SET_UNARY_UNION, INT_RANGE),
    FILTER(Set.of(SET), SET_FILTER),
    MAP(Set.of(SET), SET_MAP),
    QUANTIFIED(Set.of(QUANTIFIER, SET), FORALL3, EXISTS3, CHOOSE3),
    UNBOUNDED(Set.of(UNBOUND), FORALL2, EXISTS2, CHOOSE2),
    CONTROL_FLOW(Set.of(CONTROL), CASE, CASE_OTHER, IF_THEN_ELSE),
    LABELLED(Set.of(ExpressionCategory.LABEL), TlaOperators.LABEL),
    APPLICATION(Set.of(OPERATOR), OPER_APP),
    FINITE(Set.of(FINITE_SET, SET), IS_FINITE_SET, CARDINALITY),
    SEQUENCES(Set.of(SEQUENCE), HEAD, TAIL, APPEND, CONCAT, LEN, SUB_SEQ),
    SEQUENCE_SET(Set.of(SEQUENCE, SET), SEQ),
    RECORDS(Set.of(ExpressionCategory.RECORD), TlaOperators.RECORD),
    RECORD_SETS(Set.of(ExpressionCategory.RECORD, SET), RECORD_SET),
    PRODUCT(Set.of(ExpressionCategory.TUPLE, SET), SET_TIMES),
    FUNCTION_SET(Set.of(FUNCTION, SET), FUN_SET),
    FUNCTION_CTOR(Set.of(FUNCTION, SET), FUN_CTOR),
    DOMAIN_OF(Set.of(FUNCTION, SET), DOMAIN),
    // Application, EXCEPT and tuple syntax have type-dependent capabilities.
    STRUCTURAL(Set.of(), FUN_APP, EXCEPT),
    TUPLE_VALUE(Set.of(), TlaOperators.TUPLE),
    VARIANTS(Set.of(ExpressionCategory.VARIANT),
            TlaOperators.VARIANT, VARIANT_TAG, VARIANT_GET_OR_ELSE, VARIANT_GET_UNSAFE),
    VARIANT_FILTER(Set.of(ExpressionCategory.VARIANT, SET),
            TlaOperators.VARIANT_FILTER),
    FOLDS(Set.of(FOLD, OPERATOR), APA_FOLD_SET, APA_FOLD_SEQ_LEFT);

    private static final Map<TlaOper, LibrarySyntax> BY_OPERATOR = index();
    final Set<ExpressionCategory> categories;
    private final TlaOper[] operators;

    LibrarySyntax(Set<ExpressionCategory> categories, TlaOper... operators) {
        this.categories = categories;
        this.operators = operators;
    }

    static LibrarySyntax of(TlaOper operator) {
        var syntax = BY_OPERATOR.get(operator);
        if (syntax == null) {
            throw new IllegalArgumentException("unsupported custom operator syntax: " + operator
                    + " (action, temporal, recursive and tool-specific operators are not supported)");
        }
        return syntax;
    }

    private static Map<TlaOper, LibrarySyntax> index() {
        var result = new IdentityHashMap<TlaOper, LibrarySyntax>();
        for (var syntax : values()) {
            for (var operator : syntax.operators) {
                if (result.put(operator, syntax) != null) {
                    throw new ExceptionInInitializerError("duplicate imported syntax " + operator);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
