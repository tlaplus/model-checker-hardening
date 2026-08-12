package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.List;

/**
 * Factory for deferred generators of internal types within the configured remaining-depth budget.
 *
 * <p>The returned generators capture this run's {@link GenerationContext}, but consume no bytes
 * until they are invoked with a draw.
 */
final class IrTypeGenFactory {
    private static final List<TypeKind> PRIMITIVE_TYPE_KINDS = List.of(
            TypeKind.BOOL, TypeKind.INT, TypeKind.STRING, TypeKind.CONSTANT);
    private static final List<TypeKind> VALUE_TYPE_KINDS = List.of(
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
    private static final List<TypeKind> ALL_TYPE_KINDS = List.of(TypeKind.values());

    private final GenerationContext context;

    IrTypeGenFactory(GenerationContext context) {
        this.context = context;
    }

    /** Returns a generator that permits every type kind, including operator types. */
    Generator<IrType> anyType() {
        return mkGen(context.config().maximumTypeDepth(), true);
    }

    /** Returns a generator of non-operator types for use as expression values. */
    Generator<IrType> valueType() {
        return mkGen(context.config().maximumTypeDepth(), false);
    }

    /** Creates a single-tag variant carrying the supplied payload type. */
    VariantType singleVariant(IrType payloadType) {
        return new VariantType(List.of(new Field(context.freshTag(), payloadType)));
    }

    /** Returns a recursive type recipe within the remaining nesting budget. */
    private Generator<IrType> mkGen(int remainingDepth, boolean allowOperator) {
        return draw -> {
            var primitiveOnly = remainingDepth == 0;
            var kinds = primitiveOnly
                    ? PRIMITIVE_TYPE_KINDS
                    : (allowOperator ? ALL_TYPE_KINDS : VALUE_TYPE_KINDS);
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

    /** Type choices in decoder order. */
    private enum TypeKind {
        BOOL,
        INT,
        STRING,
        CONSTANT,
        SET,
        SEQUENCE,
        FUNCTION,
        TUPLE,
        RECORD,
        VARIANT,
        OPERATOR
    }
}
