package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.Set;

/** Expression forms not covered by the dedicated type and general families. */
public enum OtherExpressionKind implements ExpressionKind {
    STRING_LITERAL(ExpressionCategory.CORE),
    VARIANT_TAG(ExpressionCategory.VARIANT),
    MODEL_VALUE(ExpressionCategory.MODEL),
    PARSED_MODEL_VALUE(ExpressionCategory.MODEL),
    FUNCTION_DEFINITION(ExpressionCategory.FUNCTION, ExpressionCategory.SET),
    EXCEPT(ExpressionCategory.FUNCTION, ExpressionCategory.SET),
    EXCEPT_MANY(ExpressionCategory.FUNCTION, ExpressionCategory.SET),
    TUPLE_LITERAL(ExpressionCategory.TUPLE),
    RECORD_LITERAL(ExpressionCategory.RECORD),
    VARIANT_LITERAL(ExpressionCategory.VARIANT),
    LAMBDA(ExpressionCategory.OPERATOR);

    private final ExpressionCategory category;
    private final Set<ExpressionCategory> requiredCategories;

    OtherExpressionKind(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        this.category = category;
        requiredCategories = ExpressionKind.requirements(category, dependencies);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return switch (this) {
            case STRING_LITERAL, VARIANT_TAG -> type == PrimitiveType.STRING;
            case MODEL_VALUE, PARSED_MODEL_VALUE -> type instanceof ConstantType;
            case FUNCTION_DEFINITION, EXCEPT, EXCEPT_MANY ->
                type instanceof FunctionType;
            case TUPLE_LITERAL -> type instanceof TupleType;
            case RECORD_LITERAL -> type instanceof RecordType;
            case VARIANT_LITERAL -> type instanceof VariantType;
            case LAMBDA -> type instanceof OperatorType;
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
