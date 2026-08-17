package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Factory for deferred generators of internal types within the configured remaining-depth budget.
 *
 * <p>The returned generators capture this run's {@link GenerationContext}, but consume no bytes
 * until they are invoked with a draw.
 */
final class IrTypeGenFactory {
    private static final List<TypeKind> PRIMITIVE_TYPE_CANDIDATES = List.of(
            TypeKind.BOOL, TypeKind.INT, TypeKind.STRING, TypeKind.CONSTANT);
    private static final List<TypeKind> VALUE_TYPE_CANDIDATES = List.of(
            TypeKind.BOOL,
            TypeKind.INT,
            TypeKind.STRING,
            TypeKind.CONSTANT,
            TypeKind.SET,
            TypeKind.SEQUENCE,
            TypeKind.FUNCTION,
            TypeKind.TUPLE,
            TypeKind.RECORD,
            TypeKind.VARIANT);
    private static final List<TypeKind> ALL_TYPE_CANDIDATES = List.of(TypeKind.values());

    private final GenerationContext context;
    private final List<TypeKind> primitiveTypeKinds;
    private final List<TypeKind> valueTypeKinds;
    private final List<TypeKind> allTypeKinds;

    IrTypeGenFactory(GenerationContext context) {
        this.context = context;
        primitiveTypeKinds = enabledKinds(PRIMITIVE_TYPE_CANDIDATES);
        valueTypeKinds = enabledKinds(VALUE_TYPE_CANDIDATES);
        allTypeKinds = enabledKinds(ALL_TYPE_CANDIDATES);
    }

    /** Returns a generator of enabled type kinds, including enabled operator types. */
    Generator<IrType> anyType() {
        return mkGen(context.config().maximumTypeDepth(), true);
    }

    /** Returns a generator of enabled non-operator types for use as expression values. */
    Generator<IrType> valueType() {
        return mkGen(context.config().maximumTypeDepth(), false);
    }

    /** Creates a single-tag variant carrying the supplied payload type. */
    VariantType singleVariant(IrType payloadType) {
        return new VariantType(List.of(new Field(context.freshTag(), payloadType)));
    }

    /** Reports whether the type and every nested component use enabled syntax categories. */
    boolean isEnabled(IrType type) {
        return switch (type) {
            case PrimitiveType ignored -> true;
            case ConstantType ignored -> isEnabledCategory(ExpressionCategory.MODEL);
            case SetType(IrType element) ->
                isEnabledCategory(ExpressionCategory.SET) && isEnabled(element);
            case SequenceType(IrType element) ->
                isEnabledCategory(ExpressionCategory.SEQUENCE) && isEnabled(element);
            case FunctionType(IrType argument, IrType result) ->
                isEnabledCategory(ExpressionCategory.FUNCTION)
                        && isEnabledCategory(ExpressionCategory.SET)
                        && isEnabled(argument)
                        && isEnabled(result);
            case TupleType(List<IrType> elements) ->
                isEnabledCategory(ExpressionCategory.TUPLE)
                        && elements.stream().allMatch(this::isEnabled);
            case RecordType(List<Field> fields) ->
                isEnabledCategory(ExpressionCategory.RECORD)
                        && fields.stream().allMatch(field -> isEnabled(field.type()));
            case VariantType(List<Field> fields) ->
                isEnabledCategory(ExpressionCategory.VARIANT)
                        && fields.stream().allMatch(field -> isEnabled(field.type()));
            case OperatorType(List<IrType> arguments, IrType result) ->
                isEnabledCategory(ExpressionCategory.OPERATOR)
                        && arguments.stream().allMatch(this::isEnabled)
                        && isEnabled(result);
        };
    }

    /** Returns a recursive type recipe within the remaining nesting budget. */
    private Generator<IrType> mkGen(int remainingDepth, boolean allowOperator) {
        return draw -> {
            var primitiveOnly = remainingDepth == 0;
            var kinds = primitiveOnly
                    ? primitiveTypeKinds
                    : (allowOperator ? allTypeKinds : valueTypeKinds);
            return switch (draw.choose(kinds)) {
                case BOOL -> PrimitiveType.BOOL;
                case INT -> PrimitiveType.INT;
                case STRING -> PrimitiveType.STRING;
                case CONSTANT -> new ConstantType("MODEL");
                case SET -> draw.draw(
                        mkGen(remainingDepth - 1, false).map(SetType::new));
                case SEQUENCE -> draw.draw(
                        mkGen(remainingDepth - 1, false).map(SequenceType::new));
                case FUNCTION -> {
                    var component = mkGen(remainingDepth - 1, false);
                    yield draw.draw(component.flatMap(argument -> component.map(
                            result -> new FunctionType(argument, result))));
                }
                case TUPLE -> {
                    var element = mkGen(remainingDepth - 1, false);
                    yield draw.draw(BasicGenerators.listOf(
                                    element,
                                    1,
                                    context.config().maximumCollectionSize())
                            .map(TupleType::new));
                }
                case RECORD -> {
                    var fieldType = mkGen(remainingDepth - 1, false);
                    Generator<Field> field = fieldDraw -> {
                        var fieldName = context.freshField();
                        return fieldDraw.draw(fieldType.map(
                                type -> new Field(fieldName, type)));
                    };
                    yield draw.draw(BasicGenerators.listOf(
                                    field,
                                    1,
                                    context.config().maximumCollectionSize())
                            .map(RecordType::new));
                }
                case VARIANT -> {
                    var payloadType = mkGen(remainingDepth - 1, false);
                    Generator<Field> field = fieldDraw -> {
                        var tag = context.freshTag();
                        return fieldDraw.draw(payloadType.map(
                                type -> new Field(tag, type)));
                    };
                    yield draw.draw(BasicGenerators.listOf(
                                    field,
                                    1,
                                    context.config().maximumCollectionSize())
                            .map(VariantType::new));
                }
                case OPERATOR -> {
                    var component = mkGen(remainingDepth - 1, false);
                    yield draw.draw(BasicGenerators.listOf(
                                    component,
                                    0,
                                    context.config().maximumCollectionSize())
                            .flatMap(arguments -> component.map(
                                    result -> new OperatorType(arguments, result))));
                }
            };
        };
    }

    private List<TypeKind> enabledKinds(List<TypeKind> candidates) {
        return candidates.stream()
                .filter(kind -> kind.isAvailableWith(context.config().ignoredCategories()))
                .toList();
    }

    private boolean isEnabledCategory(ExpressionCategory category) {
        return !context.config().ignoredCategories().contains(category);
    }

    /** Type choices in decoder order. */
    private enum TypeKind {
        BOOL(ExpressionCategory.CORE),
        INT(ExpressionCategory.CORE),
        STRING(ExpressionCategory.CORE),
        CONSTANT(ExpressionCategory.MODEL),
        SET(ExpressionCategory.SET),
        SEQUENCE(ExpressionCategory.SEQUENCE),
        FUNCTION(ExpressionCategory.FUNCTION, ExpressionCategory.SET),
        TUPLE(ExpressionCategory.TUPLE),
        RECORD(ExpressionCategory.RECORD),
        VARIANT(ExpressionCategory.VARIANT),
        OPERATOR(ExpressionCategory.OPERATOR);

        private final Set<ExpressionCategory> requiredCategories;

        TypeKind(ExpressionCategory category, ExpressionCategory... dependencies) {
            var requirements = EnumSet.of(category);
            Collections.addAll(requirements, dependencies);
            requiredCategories = Set.copyOf(requirements);
        }

        boolean isAvailableWith(Set<ExpressionCategory> ignoredCategories) {
            for (var category : requiredCategories) {
                if (ignoredCategories.contains(category)) {
                    return false;
                }
            }
            return true;
        }
    }
}
