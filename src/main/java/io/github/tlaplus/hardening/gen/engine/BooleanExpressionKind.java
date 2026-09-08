package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;

/** Boolean-valued expression forms. */
public enum BooleanExpressionKind implements ExpressionKind {
    BOOLEAN_LITERAL(ExpressionCategory.CORE),
    EQUAL(ExpressionCategory.BOOL_LOGIC),
    NOT_EQUAL(ExpressionCategory.BOOL_LOGIC),
    NOT(ExpressionCategory.BOOL_LOGIC),
    AND(ExpressionCategory.BOOL_LOGIC),
    OR(ExpressionCategory.BOOL_LOGIC),
    IMPLIES(ExpressionCategory.BOOL_LOGIC),
    EQUIVALENT(ExpressionCategory.BOOL_LOGIC),
    FORALL_BOUNDED(ExpressionCategory.QUANTIFIER, ExpressionCategory.SET),
    EXISTS_BOUNDED(ExpressionCategory.QUANTIFIER, ExpressionCategory.SET),
    FORALL_UNBOUNDED(ExpressionCategory.UNBOUND),
    EXISTS_UNBOUNDED(ExpressionCategory.UNBOUND),
    LESS_THAN(ExpressionCategory.ARITHMETIC),
    GREATER_THAN(ExpressionCategory.ARITHMETIC),
    LESS_EQUAL(ExpressionCategory.ARITHMETIC),
    GREATER_EQUAL(ExpressionCategory.ARITHMETIC),
    IN(ExpressionCategory.SET),
    NOT_IN(ExpressionCategory.SET),
    SUBSET_EQUAL(ExpressionCategory.SET),
    IS_FINITE_SET(ExpressionCategory.FINITE_SET, ExpressionCategory.SET),
    PRIME_EQUAL(ExpressionCategory.ACTION),
    STUTTER(ExpressionCategory.TEMPORAL),
    NO_STUTTER(ExpressionCategory.TEMPORAL),
    ENABLED(ExpressionCategory.TEMPORAL),
    UNCHANGED(ExpressionCategory.ACTION),
    ACTION_THEN(ExpressionCategory.EXOTIC),
    ALWAYS(ExpressionCategory.TEMPORAL),
    EVENTUALLY(ExpressionCategory.TEMPORAL),
    LEADS_TO(ExpressionCategory.TEMPORAL),
    GUARANTEES(ExpressionCategory.TEMPORAL),
    WEAK_FAIR(ExpressionCategory.TEMPORAL),
    STRONG_FAIR(ExpressionCategory.TEMPORAL),
    TEMPORAL_EXISTS(ExpressionCategory.EXOTIC),
    TEMPORAL_FORALL(ExpressionCategory.EXOTIC);

    private final Categories categories;

    BooleanExpressionKind(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        categories = new Categories(category, dependencies);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type == PrimitiveType.BOOL;
    }

    @Override
    public Categories categories() {
        return categories;
    }
}
