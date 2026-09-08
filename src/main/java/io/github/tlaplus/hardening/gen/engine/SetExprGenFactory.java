package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.List;
import org.apalache_mc.tla.jir.ExpressionPair;

/** Constructs set-valued expression generators. */
final class SetExprGenFactory extends AbstractExprGenFactory {
    SetExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected set form. */
    Generator<TlaEx> mkGen(
            SetExpressionKind kind, SetType type, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case EMPTY_SET -> builder().emptySet(type.element().toTlaType());
                case ENUM_SET -> builder().enumSet(
                        draw.draw(operands(type.element(), nextDepth)));
                case SET_INTERSECTION -> draw.draw(binary(type, nextDepth, builder()::intersect));
                case SET_UNION -> draw.draw(binary(type, nextDepth, builder()::union));
                case SET_DIFFERENCE -> draw.draw(binary(type, nextDepth, builder()::difference));
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
                case INTERVAL -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::interval));
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
    private Generator<TlaEx> filter(SetType resultType, int remainingDepth) {
        return bounded("filtered", resultType.element(), PrimitiveType.BOOL,
                remainingDepth - 1, builder()::filter);
    }

    /** Returns a set-map generator from a generated source type. */
    private Generator<TlaEx> map(
            IrType resultElementType, int remainingDepth) {
        return typeFactory.valueType().flatMap(sourceType -> bounded(
                "mapped", sourceType, resultElementType, remainingDepth - 1,
                (variable, source, body) -> builder().map(
                        body, BuilderArrays.pairs(List.of(new ExpressionPair<>(variable, source))))));
    }

    /**
     * Returns a generator of a set containing a generated row-record value.
     *
     * <p>The builder's native record-set form currently produces the legacy record type,
     * which is incompatible with the row-record type produced by its record-value form.
     */
    private Generator<TlaEx> recordSet(RecordType type, int remainingDepth) {
        return record(type, field -> expression(field, remainingDepth - 1)).map(builder()::enumSet);
    }

    /** Returns a Cartesian-product generator for the tuple component types. */
    private Generator<TlaEx> cartesianProduct(
            TupleType type, int remainingDepth) {
        return draw -> builder().times(BuilderArrays.expressions(type.elements().stream()
                .map(element -> draw.draw(expression(
                        new SetType(element), remainingDepth - 1)))
                .toList()));
    }

    /** Returns a variant-filter generator that extracts the requested payload type. */
    private Generator<TlaEx> variantFilter(
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
