package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.Set;

/** Type-polymorphic and terminal expression forms. */
public enum GeneralExpressionKind implements ExpressionKind {
    TERMINAL(ExpressionCategory.CORE),
    NAME(ExpressionCategory.CORE),
    IF_THEN_ELSE(ExpressionCategory.CONTROL),
    LABEL(ExpressionCategory.LABEL),
    BOUNDED_CHOOSE(ExpressionCategory.QUANTIFIER, ExpressionCategory.SET),
    UNBOUNDED_CHOOSE(ExpressionCategory.UNBOUND),
    CASE(ExpressionCategory.CONTROL),
    OPERATOR_APPLICATION(ExpressionCategory.OPERATOR),
    LET(ExpressionCategory.OPERATOR),
    PRIME(ExpressionCategory.ACTION),
    FUNCTION_APPLICATION(ExpressionCategory.FUNCTION, ExpressionCategory.SET),
    FOLD_SET(
            ExpressionCategory.FOLD,
            ExpressionCategory.SET,
            ExpressionCategory.OPERATOR),
    FOLD_SEQUENCE(
            ExpressionCategory.FOLD,
            ExpressionCategory.SEQUENCE,
            ExpressionCategory.OPERATOR),
    HEAD(ExpressionCategory.SEQUENCE),
    VARIANT_GET_OR_ELSE(ExpressionCategory.VARIANT),
    VARIANT_GET_UNSAFE(ExpressionCategory.VARIANT);

    private final ExpressionCategory category;
    private final Set<ExpressionCategory> requiredCategories;

    GeneralExpressionKind(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        this.category = category;
        requiredCategories = ExpressionKind.requirements(category, dependencies);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return switch (this) {
            case TERMINAL, NAME -> true;
            default -> !(type instanceof OperatorType);
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code TERMINAL} takes its configured weight only while a binding of the requested type
     * is visible, because that is the case where it contributes a name. It is applicable to every
     * type, so weighting its closed-constant case would shrink every expression instead of
     * biasing towards the surrounding context.
     */
    @Override
    public int selectionWeight(GenerationContext context, IrType type) {
        var config = context.config();
        return switch (this) {
            case NAME -> context.hasBinding(type)
                    ? config.weightOf(this)
                    : 0;
            case OPERATOR_APPLICATION -> context.hasOperatorReturning(type)
                    ? config.weightOf(this)
                    : 0;
            case TERMINAL -> context.hasBinding(type)
                    ? config.weightOf(this)
                    : ExpressionKind.DEFAULT_WEIGHT;
            default -> config.weightOf(this);
        };
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
