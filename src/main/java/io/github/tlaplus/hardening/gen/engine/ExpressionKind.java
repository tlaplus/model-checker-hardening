package io.github.tlaplus.hardening.gen.engine;

import java.util.ArrayList;
import java.util.List;

/** A selectable expression form grouped by the component that constructs it. */
sealed interface ExpressionKind
        permits GeneralExpressionKind,
                BooleanExpressionKind,
                IntegerExpressionKind,
                SetExpressionKind,
                SequenceExpressionKind,
                OtherExpressionKind {
    /** Reports whether this form can produce the requested type. */
    boolean isApplicable(IrType type);
}

/** Type-polymorphic and terminal expression forms. */
enum GeneralExpressionKind implements ExpressionKind {
    TERMINAL,
    NAME,
    VARIABLE_DECLARATION,
    IF_THEN_ELSE,
    LABEL,
    BOUNDED_CHOOSE,
    UNBOUNDED_CHOOSE,
    CASE,
    OPERATOR_APPLICATION,
    LET,
    PRIME,
    FUNCTION_APPLICATION,
    FOLD_SET,
    FOLD_SEQUENCE,
    HEAD,
    VARIANT_GET_OR_ELSE,
    VARIANT_GET_UNSAFE;

    @Override
    public boolean isApplicable(IrType type) {
        return switch (this) {
            case TERMINAL -> true;
            default -> !(type instanceof OperatorType);
        };
    }
}

/** Boolean-valued expression forms. */
enum BooleanExpressionKind implements ExpressionKind {
    BOOLEAN_LITERAL,
    EQUAL,
    NOT_EQUAL,
    NOT,
    AND,
    OR,
    IMPLIES,
    EQUIVALENT,
    FORALL_BOUNDED,
    EXISTS_BOUNDED,
    FORALL_UNBOUNDED,
    EXISTS_UNBOUNDED,
    LESS_THAN,
    GREATER_THAN,
    LESS_EQUAL,
    GREATER_EQUAL,
    IN,
    NOT_IN,
    SUBSET_EQUAL,
    IS_FINITE_SET,
    PRIME_EQUAL,
    STUTTER,
    NO_STUTTER,
    ENABLED,
    UNCHANGED,
    ACTION_THEN,
    ALWAYS,
    EVENTUALLY,
    LEADS_TO,
    GUARANTEES,
    WEAK_FAIR,
    STRONG_FAIR,
    TEMPORAL_EXISTS,
    TEMPORAL_FORALL;

    @Override
    public boolean isApplicable(IrType type) {
        return type == PrimitiveType.BOOL;
    }
}

/** Integer-valued expression forms. */
enum IntegerExpressionKind implements ExpressionKind {
    INTEGER_LITERAL,
    PLUS,
    MINUS,
    UNARY_MINUS,
    MULTIPLY,
    DIVIDE,
    MODULO,
    EXPONENT,
    CARDINALITY,
    LENGTH;

    @Override
    public boolean isApplicable(IrType type) {
        return type == PrimitiveType.INT;
    }
}

/** Set-valued expression forms. */
enum SetExpressionKind implements ExpressionKind {
    EMPTY_SET,
    ENUM_SET,
    SET_INTERSECTION,
    SET_UNION,
    SET_DIFFERENCE,
    UNION_ALL,
    SET_FILTER,
    SET_MAP,
    FUNCTION_SET,
    RECORD_SET,
    SEQUENCE_SET,
    CARTESIAN_PRODUCT,
    POWER_SET,
    INTERVAL,
    BOOLEAN_SET,
    STRING_SET,
    INTEGER_SET,
    NATURAL_SET,
    VARIANT_FILTER,
    DOMAIN;

    @Override
    public boolean isApplicable(IrType type) {
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
}

/** Sequence-valued expression forms. */
enum SequenceExpressionKind implements ExpressionKind {
    EMPTY_SEQUENCE,
    SEQUENCE_LITERAL,
    APPEND,
    CONCATENATE,
    TAIL,
    SUBSEQUENCE;

    @Override
    public boolean isApplicable(IrType type) {
        return type instanceof SequenceType;
    }
}

/** Expression forms not covered by the dedicated type and general families. */
enum OtherExpressionKind implements ExpressionKind {
    STRING_LITERAL,
    VARIANT_TAG,
    MODEL_VALUE,
    PARSED_MODEL_VALUE,
    FUNCTION_DEFINITION,
    EXCEPT,
    EXCEPT_MANY,
    TUPLE_LITERAL,
    RECORD_LITERAL,
    VARIANT_LITERAL,
    LAMBDA;

    @Override
    public boolean isApplicable(IrType type) {
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
}

/** Static expression-kind catalog used by indexed selection. */
final class ExpressionKinds {
    private static final int SINGLE_BYTE_CHOICE_COUNT = 1 << Byte.SIZE;
    private static final List<ExpressionKind> ALL = buildCatalog();

    static {
        if (ALL.size() > SINGLE_BYTE_CHOICE_COUNT) {
            throw new ExceptionInInitializerError(
                    "expression kinds must fit in a single-byte choice");
        }
    }

    private ExpressionKinds() {}

    /**
     * Returns every form in decoder order. Family order and each enum's declaration order are the
     * implementation-local byte encoding. The catalog is built once, not during expression draws.
     */
    static List<ExpressionKind> all() {
        return ALL;
    }

    /** Concatenates the family enums once in their documented decoder order. */
    private static List<ExpressionKind> buildCatalog() {
        var result = new ArrayList<ExpressionKind>();
        result.addAll(List.of(GeneralExpressionKind.values()));
        result.addAll(List.of(BooleanExpressionKind.values()));
        result.addAll(List.of(IntegerExpressionKind.values()));
        result.addAll(List.of(SetExpressionKind.values()));
        result.addAll(List.of(SequenceExpressionKind.values()));
        result.addAll(List.of(OtherExpressionKind.values()));
        return List.copyOf(result);
    }
}
