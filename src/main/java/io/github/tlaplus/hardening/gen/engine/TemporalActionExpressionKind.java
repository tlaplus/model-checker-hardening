package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;

/**
 * The temporal formulas over an action that <em>Specifying Systems</em> defines: an action occurs in
 * a temporal formula only through them and through fairness.
 *
 * <p>{@code [A]_v} and {@code <<A>>_v} alone are actions, and {@code []A} for an action {@code A}
 * is not a formula, so these forms apply {@code []} and {@code <>} to the subscripted action as one
 * construct. The family comes last in the catalog, so adding it did not shift the selection index
 * of any earlier form.
 */
public enum TemporalActionExpressionKind implements ExpressionKind {
    /** {@code [][A]_v}: every step satisfies the action or leaves v unchanged. */
    ALWAYS_ACTION,
    /** {@code <><<A>>_v}: some step satisfies the action and changes v. */
    EVENTUALLY_ACTION;

    private static final Categories CATEGORIES = new Categories(ExpressionCategory.TEMPORAL);

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type == PrimitiveType.BOOL;
    }

    @Override
    public Level level() {
        return Level.TEMPORAL;
    }

    @Override
    public Categories categories() {
        return CATEGORIES;
    }
}
