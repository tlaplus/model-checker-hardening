package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.Set;

/** Integer-valued expression forms. */
public enum IntegerExpressionKind implements ExpressionKind {
    INTEGER_LITERAL(ExpressionCategory.CORE),
    PLUS(ExpressionCategory.ARITHMETIC),
    MINUS(ExpressionCategory.ARITHMETIC),
    UNARY_MINUS(ExpressionCategory.ARITHMETIC),
    MULTIPLY(ExpressionCategory.ARITHMETIC),
    DIVIDE(ExpressionCategory.ARITHMETIC),
    MODULO(ExpressionCategory.ARITHMETIC),
    EXPONENT(ExpressionCategory.ARITHMETIC),
    CARDINALITY(ExpressionCategory.FINITE_SET, ExpressionCategory.SET),
    LENGTH(ExpressionCategory.SEQUENCE);

    private final ExpressionCategory category;
    private final Set<ExpressionCategory> requiredCategories;

    IntegerExpressionKind(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        this.category = category;
        requiredCategories = ExpressionKind.requirements(category, dependencies);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type == PrimitiveType.INT;
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
