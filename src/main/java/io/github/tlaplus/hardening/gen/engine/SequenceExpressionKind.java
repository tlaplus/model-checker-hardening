package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.Set;

/** Sequence-valued expression forms. */
public enum SequenceExpressionKind implements ExpressionKind {
    EMPTY_SEQUENCE(ExpressionCategory.SEQUENCE),
    SEQUENCE_LITERAL(ExpressionCategory.SEQUENCE),
    APPEND(ExpressionCategory.SEQUENCE),
    CONCATENATE(ExpressionCategory.SEQUENCE),
    TAIL(ExpressionCategory.SEQUENCE),
    SUBSEQUENCE(ExpressionCategory.SEQUENCE);

    private final ExpressionCategory category;
    private final Set<ExpressionCategory> requiredCategories;

    SequenceExpressionKind(ExpressionCategory category) {
        this.category = category;
        requiredCategories = ExpressionKind.requirements(category);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type instanceof SequenceType;
    }

    @Override
    public ExpressionCategory category() {
        return category;
    }

    @Override
    public Set<ExpressionCategory> requiredCategories() {
        return requiredCategories;
    }
}
