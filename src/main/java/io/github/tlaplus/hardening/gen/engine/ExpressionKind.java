package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A selectable expression form grouped by the component that constructs it. */
sealed interface ExpressionKind
        permits GeneralExpressionKind,
                BooleanExpressionKind,
                IntegerExpressionKind,
                SetExpressionKind,
                SequenceExpressionKind,
                OtherExpressionKind {
    /** Reports whether this form can produce the requested type, independent of lexical scope. */
    boolean isTypeApplicable(IrType type);

    /**
     * Reports whether the current lexical scope can supply what this form needs. Most forms build
     * everything they use, so the default is true; a form that refers to an existing binding says
     * so here rather than being special-cased by the selector.
     */
    default boolean isScopeApplicable(GenerationContext context, IrType type) {
        return true;
    }

    /** Returns this form's single primary user-facing category. */
    ExpressionCategory category();

    /** Returns the syntax capabilities required to construct this form. */
    Set<ExpressionCategory> requiredCategories();

    /** Reports whether this form requires one of the supplied exclusion categories. */
    default boolean isUnavailableWith(Set<ExpressionCategory> ignoredCategories) {
        for (var category : requiredCategories()) {
            if (ignoredCategories.contains(category)) {
                return true;
            }
        }
        return false;
    }

    /** Builds an immutable requirement set containing the primary category. */
    static Set<ExpressionCategory> requirements(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        var result = EnumSet.of(category);
        Collections.addAll(result, dependencies);
        return Set.copyOf(result);
    }
}

/** Type-polymorphic and terminal expression forms. */
enum GeneralExpressionKind implements ExpressionKind {
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

    @Override
    public boolean isScopeApplicable(GenerationContext context, IrType type) {
        return switch (this) {
            case NAME -> context.hasBinding(type);
            case OPERATOR_APPLICATION -> context.hasOperatorReturning(type);
            default -> true;
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

/** Boolean-valued expression forms. */
enum BooleanExpressionKind implements ExpressionKind {
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
    PRIME_EQUAL(ExpressionCategory.ACTION),
    STUTTER(ExpressionCategory.TEMPORAL),
    NO_STUTTER(ExpressionCategory.TEMPORAL),
    ENABLED(ExpressionCategory.TEMPORAL),
    UNCHANGED(ExpressionCategory.ACTION),
    ACTION_THEN(ExpressionCategory.EXOTIC),
    ALWAYS(ExpressionCategory.TEMPORAL),
    EVENTUALLY(ExpressionCategory.TEMPORAL),
    LEADS_TO(ExpressionCategory.TEMPORAL),
    GUARANTEES(ExpressionCategory.TEMPORAL),
    WEAK_FAIR(ExpressionCategory.TEMPORAL),
    STRONG_FAIR(ExpressionCategory.TEMPORAL),
    TEMPORAL_EXISTS(ExpressionCategory.EXOTIC),
    TEMPORAL_FORALL(ExpressionCategory.EXOTIC);

    private final ExpressionCategory category;
    private final Set<ExpressionCategory> requiredCategories;

    BooleanExpressionKind(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        this.category = category;
        requiredCategories = ExpressionKind.requirements(category, dependencies);
    }

    @Override
    public boolean isTypeApplicable(IrType type) {
        return type == PrimitiveType.BOOL;
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

/** Integer-valued expression forms. */
enum IntegerExpressionKind implements ExpressionKind {
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

/** Set-valued expression forms. */
enum SetExpressionKind implements ExpressionKind {
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

    private final ExpressionCategory category;
    private final Set<ExpressionCategory> requiredCategories;

    SetExpressionKind(ExpressionCategory category, ExpressionCategory... dependencies) {
        this.category = category;
        requiredCategories = ExpressionKind.requirements(category, dependencies);
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
    public ExpressionCategory category() {
        return category;
    }

    @Override
    public Set<ExpressionCategory> requiredCategories() {
        return requiredCategories;
    }
}

/** Sequence-valued expression forms. */
enum SequenceExpressionKind implements ExpressionKind {
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

/** Expression forms not covered by the dedicated type and general families. */
enum OtherExpressionKind implements ExpressionKind {
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
