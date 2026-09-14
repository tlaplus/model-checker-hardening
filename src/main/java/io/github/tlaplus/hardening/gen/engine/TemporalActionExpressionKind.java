package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;

/**
 * Temporal formulas built around an action, in the only nested forms both checkers accept.
 *
 * <p>TLC rejects an action inside a temporal formula unless it has one of these forms, and a
 * standalone {@code <<A>>_v} or a nested {@code [][A]_v} is rejected with "Temporal formulas
 * containing actions must be of forms <>[]A or []<>A". The family comes last in the catalog, so
 * adding it did not shift the selection index of any earlier form.
 */
public enum TemporalActionExpressionKind implements ExpressionKind {
    /** {@code []<><<A>>_v}: the action occurs infinitely often. */
    INFINITELY_OFTEN_ACTION,
    /** {@code <>[][A]_v}: eventually every step satisfies the action or leaves v unchanged. */
    EVENTUALLY_ALWAYS_ACTION;

    private static final Categories CATEGORIES = new Categories(ExpressionCategory.TEMPORAL);

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type == PrimitiveType.BOOL;
    }

    @Override
    public Level level() {
        return Level.ACTION_TEMPORAL;
    }

    @Override
    public Categories categories() {
        return CATEGORIES;
    }
}
