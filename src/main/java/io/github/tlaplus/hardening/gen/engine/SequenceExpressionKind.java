package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;

/** Sequence-valued expression forms. */
public enum SequenceExpressionKind implements ExpressionKind {
    EMPTY_SEQUENCE(ExpressionCategory.SEQUENCE),
    SEQUENCE_LITERAL(ExpressionCategory.SEQUENCE),
    APPEND(ExpressionCategory.SEQUENCE),
    CONCATENATE(ExpressionCategory.SEQUENCE),
    TAIL(ExpressionCategory.SEQUENCE),
    SUBSEQUENCE(ExpressionCategory.SEQUENCE);

    private final Categories categories;

    SequenceExpressionKind(ExpressionCategory category) {
        categories = new Categories(category);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type instanceof SequenceType;
    }

    @Override
    public Categories categories() {
        return categories;
    }
}
