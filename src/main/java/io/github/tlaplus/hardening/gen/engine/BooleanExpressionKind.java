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
    PRIME_EQUAL(Level.ACTION, ExpressionCategory.ACTION),
    STUTTER(Level.ACTION, ExpressionCategory.TEMPORAL),
    NO_STUTTER(Level.ACTION, ExpressionCategory.TEMPORAL),
    ENABLED(ExpressionCategory.TEMPORAL),
    UNCHANGED(Level.ACTION, ExpressionCategory.ACTION),
    ACTION_THEN(Level.ACTION, ExpressionCategory.EXOTIC),
    ALWAYS(Level.TEMPORAL, ExpressionCategory.TEMPORAL),
    EVENTUALLY(Level.TEMPORAL, ExpressionCategory.TEMPORAL),
    LEADS_TO(Level.TEMPORAL, ExpressionCategory.TEMPORAL),
    // Neither TLC nor Apalache checks -+->, so it is exotic although it is a temporal operator.
    GUARANTEES(Level.TEMPORAL, ExpressionCategory.EXOTIC),
    WEAK_FAIR(Level.TEMPORAL, ExpressionCategory.TEMPORAL),
    STRONG_FAIR(Level.TEMPORAL, ExpressionCategory.TEMPORAL),
    TEMPORAL_EXISTS(Level.TEMPORAL, ExpressionCategory.EXOTIC),
    TEMPORAL_FORALL(Level.TEMPORAL, ExpressionCategory.EXOTIC);

    private final Level level;
    private final Categories categories;

    BooleanExpressionKind(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        this(Level.STATE, category, dependencies);
    }

    BooleanExpressionKind(
            Level level, ExpressionCategory category, ExpressionCategory... dependencies) {
        this.level = level;
        categories = new Categories(category, dependencies);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type == PrimitiveType.BOOL;
    }

    @Override
    public Level level() {
        return level;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code PRIME_EQUAL} primes a declared state variable, so it withdraws where none is
     * visible, as in the expression entry point.
     */
    @Override
    public int selectionWeight(GenerationContext context, IrType type) {
        if (this == PRIME_EQUAL && !context.hasStateVariable()) {
            return 0;
        }
        return context.config().weightOf(this);
    }

    @Override
    public Categories categories() {
        return categories;
    }
}
