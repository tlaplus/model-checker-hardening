package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.List;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.NamedExpression;
import org.apalache_mc.tla.jir.TlaBuilderExpr;

/** Constructs set-valued expression generators. */
final class SetExprGenFactory extends AbstractExprGenFactory {
    SetExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected set form. */
    Generator<TlaBuilderExpr> mkGen(
            SetExpressionKind kind, SetType type, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case EMPTY_SET -> builder().emptySet(type.element().toTlaType());
                case ENUM_SET -> builder().enumSet(BuilderArrays.expressions(draw.draw(
                        BasicGenerators.listOf(
                                expression(type.element(), nextDepth),
                                1,
                                context.config().maximumCollectionSize()))));
                case SET_INTERSECTION -> builder().intersect(
                        draw.draw(expression(type, nextDepth)),
                        draw.draw(expression(type, nextDepth)));
                case SET_UNION -> builder().union(
                        draw.draw(expression(type, nextDepth)),
                        draw.draw(expression(type, nextDepth)));
                case SET_DIFFERENCE -> builder().difference(
                        draw.draw(expression(type, nextDepth)),
                        draw.draw(expression(type, nextDepth)));
                case UNION_ALL -> builder().unionAll(
                        draw.draw(expression(new SetType(type), nextDepth)));
                case SET_FILTER -> draw.draw(filter(type, remainingDepth));
                case SET_MAP -> draw.draw(map(type.element(), remainingDepth));
                case FUNCTION_SET -> {
                    var functionType = (FunctionType) type.element();
                    yield builder().funSet(
                            draw.draw(expression(
                                    new SetType(functionType.argument()), nextDepth)),
                            draw.draw(expression(
                                    new SetType(functionType.result()), nextDepth)));
                }
                case RECORD_SET ->
                    draw.draw(recordSet((RecordType) type.element(), remainingDepth));
                case SEQUENCE_SET -> {
                    var sequenceType = (SequenceType) type.element();
                    yield builder().seqSet(draw.draw(expression(
                            new SetType(sequenceType.element()), nextDepth)));
                }
                case CARTESIAN_PRODUCT -> draw.draw(cartesianProduct(
                        (TupleType) type.element(), remainingDepth));
                case POWER_SET -> builder().powerSet(
                        draw.draw(expression(type.element(), nextDepth)));
                case INTERVAL -> builder().interval(
                        draw.draw(expression(PrimitiveType.INT, nextDepth)),
                        draw.draw(expression(PrimitiveType.INT, nextDepth)));
                case BOOLEAN_SET -> builder().booleanSet();
                case STRING_SET -> builder().stringSet();
                case INTEGER_SET -> builder().intSet();
                case NATURAL_SET -> builder().natSet();
                case VARIANT_FILTER ->
                    draw.draw(variantFilter(type.element(), remainingDepth));
                case DOMAIN -> {
                    var resultType = draw.draw(typeFactory.valueType());
                    yield builder().domain(draw.draw(expression(
                            new FunctionType(type.element(), resultType), nextDepth)));
                }
            };
        };
    }

    /** Returns a set-filter generator whose predicate sees its bound name. */
    private Generator<TlaBuilderExpr> filter(SetType resultType, int remainingDepth) {
        return draw -> {
            var elementType = resultType.element();
            var binding = context.freshBinding("filtered", elementType);
            var variable = builder().name(binding.name(), elementType.toTlaType());
            var source = draw.draw(expression(resultType, remainingDepth - 1));
            var predicate = draw.draw(context.withBinding(
                    binding, expression(PrimitiveType.BOOL, remainingDepth - 1)));
            return builder().filter(variable, source, predicate);
        };
    }

    /** Returns a set-map generator from a generated source type. */
    private Generator<TlaBuilderExpr> map(
            IrType resultElementType, int remainingDepth) {
        return draw -> {
            var sourceType = draw.draw(typeFactory.valueType());
            var binding = context.freshBinding("mapped", sourceType);
            var variable = builder().name(binding.name(), sourceType.toTlaType());
            var source = draw.draw(expression(
                    new SetType(sourceType), remainingDepth - 1));
            var pair = new ExpressionPair<>(variable, source);
            var body = draw.draw(context.withBinding(
                    binding, expression(resultElementType, remainingDepth - 1)));
            return builder().map(body, BuilderArrays.pairs(List.of(pair)));
        };
    }

    /**
     * Returns a generator of a set containing a generated row-record value.
     *
     * <p>The checked builder's native record-set form currently produces the legacy record type,
     * which is incompatible with the row-record type produced by its record-value form.
     */
    private Generator<TlaBuilderExpr> recordSet(RecordType type, int remainingDepth) {
        return draw -> {
            var fields = type.fields().stream()
                    .map(field -> new NamedExpression<>(
                            field.name(),
                            draw.draw(expression(field.type(), remainingDepth - 1))))
                    .toList();
            return builder().enumSet(builder().record(BuilderArrays.named(fields)));
        };
    }

    /** Returns a Cartesian-product generator for the tuple component types. */
    private Generator<TlaBuilderExpr> cartesianProduct(
            TupleType type, int remainingDepth) {
        return draw -> builder().times(BuilderArrays.expressions(type.elements().stream()
                .map(element -> draw.draw(expression(
                        new SetType(element), remainingDepth - 1)))
                .toList()));
    }

    /** Returns a variant-filter generator that extracts the requested payload type. */
    private Generator<TlaBuilderExpr> variantFilter(
            IrType resultElement, int remainingDepth) {
        return draw -> {
            var variantType = typeFactory.singleVariant(resultElement);
            var tag = variantType.fields().getFirst().name();
            return builder().variantFilter(
                    tag,
                    draw.draw(expression(
                            new SetType(variantType), remainingDepth - 1)));
        };
    }
}
