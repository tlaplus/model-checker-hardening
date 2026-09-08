package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.List;

/** Set-valued expression forms. */
public enum SetExpressionKind implements ExpressionKind {
    EMPTY_SET(ExpressionCategory.SET),
    ENUM_SET(ExpressionCategory.SET),
    SET_INTERSECTION(ExpressionCategory.SET),
    SET_UNION(ExpressionCategory.SET),
    SET_DIFFERENCE(ExpressionCategory.SET),
    UNION_ALL(ExpressionCategory.SET),
    SET_FILTER(ExpressionCategory.SET),
    SET_MAP(ExpressionCategory.SET),
    FUNCTION_SET(ExpressionCategory.FUNCTION, ExpressionCategory.SET),
    RECORD_SET(ExpressionCategory.RECORD, ExpressionCategory.SET),
    SEQUENCE_SET(ExpressionCategory.SEQUENCE, ExpressionCategory.SET),
    CARTESIAN_PRODUCT(ExpressionCategory.TUPLE, ExpressionCategory.SET),
    POWER_SET(ExpressionCategory.SET),
    INTERVAL(ExpressionCategory.SET),
    BOOLEAN_SET(ExpressionCategory.UNIVERSE, ExpressionCategory.SET),
    STRING_SET(ExpressionCategory.UNIVERSE, ExpressionCategory.SET),
    INTEGER_SET(ExpressionCategory.UNIVERSE, ExpressionCategory.SET),
    NATURAL_SET(ExpressionCategory.UNIVERSE, ExpressionCategory.SET),
    VARIANT_FILTER(ExpressionCategory.VARIANT, ExpressionCategory.SET),
    DOMAIN(ExpressionCategory.FUNCTION, ExpressionCategory.SET);

    private final Categories categories;

    SetExpressionKind(ExpressionCategory category, ExpressionCategory... dependencies) {
        categories = new Categories(category, dependencies);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        if (!(type instanceof SetType(IrType setElem))) {
            return false;
        }
        return switch (this) {
            case FUNCTION_SET -> setElem instanceof FunctionType;
            case RECORD_SET -> setElem instanceof RecordType;
            case SEQUENCE_SET -> setElem instanceof SequenceType;
            case CARTESIAN_PRODUCT ->
                setElem instanceof TupleType(List<IrType> tupleElems)
                        && tupleElems.size() >= 2;
            case POWER_SET -> setElem instanceof SetType;
            case INTERVAL, INTEGER_SET, NATURAL_SET -> setElem == PrimitiveType.INT;
            case BOOLEAN_SET -> setElem == PrimitiveType.BOOL;
            case STRING_SET -> setElem == PrimitiveType.STRING;
            default -> true;
        };
    }

    @Override
    public Categories categories() {
        return categories;
    }
}
