package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.ConstT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.VariantT1;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.ArrayList;
import java.util.List;
import org.apalache_mc.tla.jir.ExceptUpdate;
import org.apalache_mc.tla.jir.ExpressionPair;

/** Constructs expression generators not covered by the dedicated form families. */
final class OtherExprGenFactory extends AbstractExprGenFactory {
    private static final String STRING_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_ ";

    OtherExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected other form. */
    Generator<TlaEx> mkGen(
            OtherExpressionKind kind, IrType type, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case STRING_LITERAL -> builder().str(draw.draw(stringLiteral()));
                case VARIANT_TAG -> {
                    var payloadType = draw.draw(typeFactory.valueType());
                    var variantType = typeFactory.singleVariant(payloadType);
                    yield builder().variantTag(
                            draw.draw(expression(variantType, nextDepth)));
                }
                case MODEL_VALUE -> {
                    var constantType = (ConstantType) type;
                    yield builder().constant(
                            context.fresh("model"), (ConstT1) constantType.toTlaType());
                }
                case PARSED_MODEL_VALUE -> {
                    var constantType = (ConstantType) type;
                    yield builder().constParsed(
                            context.fresh("model") + "_OF_" + constantType.name());
                }
                case FUNCTION_DEFINITION ->
                    draw.draw(functionDefinition((FunctionType) type, remainingDepth));
                case EXCEPT -> {
                    var functionType = (FunctionType) type;
                    yield builder().except(
                            draw.draw(expression(type, nextDepth)),
                            draw.draw(expression(functionType.argument(), nextDepth)),
                            draw.draw(expression(functionType.result(), nextDepth)));
                }
                case EXCEPT_MANY ->
                    draw.draw(exceptMany((FunctionType) type, remainingDepth));
                case TUPLE_LITERAL -> draw.draw(tuple(
                        (TupleType) type, element -> expression(element, nextDepth)));
                case RECORD_LITERAL -> draw.draw(record(
                        (RecordType) type, field -> expression(field, nextDepth)));
                case VARIANT_LITERAL -> {
                    var variantType = (VariantType) type;
                    var field = draw.choose(variantType.fields());
                    yield builder().variant(
                            field.name(),
                            draw.draw(expression(field.type(), nextDepth)),
                            (VariantT1) variantType.toTlaType());
                }
                case LAMBDA -> draw.draw(lambda((OperatorType) type, remainingDepth));
            };
        };
    }

    /** Returns a lambda generator whose body sees every typed parameter. */
    Generator<TlaEx> lambda(OperatorType type, int remainingDepth) {
        return draw -> {
            var parameters = new ArrayList<org.apalache_mc.tla.jir.TypedParameter>();
            var bindings = new ArrayList<ScopedName>();
            for (var argument : type.arguments()) {
                var binding = context.freshBinding("parameter", argument);
                bindings.add(binding);
                parameters.add(builder().param(binding.name(), argument.toTlaType()));
            }
            var lambdaName = context.fresh("Lambda");
            var body = draw.draw(context.withBindings(
                    bindings, expression(type.result(), remainingDepth - 1)));
            return builder().lambda(
                    lambdaName,
                    body,
                    parameters.toArray(
                            org.apalache_mc.tla.jir.TypedParameter[]::new));
        };
    }

    /** Returns a function-definition generator whose body sees its bounded argument. */
    private Generator<TlaEx> functionDefinition(
            FunctionType type, int remainingDepth) {
        return draw -> {
            var binding = context.freshBinding("arg", type.argument());
            var variable = builder().name(binding.name(), type.argument().toTlaType());
            var domain = draw.draw(expression(
                    new SetType(type.argument()), remainingDepth - 1));
            var pair = new ExpressionPair<>(variable, domain);
            var body = draw.draw(context.withBinding(
                    binding, expression(type.result(), remainingDepth - 1)));
            return builder().funDef(body, BuilderArrays.pairs(List.of(pair)));
        };
    }

    /** Returns a function-update generator containing a terminated update collection. */
    private Generator<TlaEx> exceptMany(
            FunctionType type, int remainingDepth) {
        return draw -> {
            var updates = draw.draw(BasicGenerators.listOf(
                    updateDraw -> new ExceptUpdate<>(
                            updateDraw.draw(expression(
                                    type.argument(), remainingDepth - 1)),
                            updateDraw.draw(expression(
                                    type.result(), remainingDepth - 1))),
                    1,
                    context.config().maximumCollectionSize()));
            return builder().exceptMany(
                    draw.draw(expression(type, remainingDepth - 1)),
                    BuilderArrays.updates(updates));
        };
    }

    /** Returns a generator that decodes a terminated string payload. */
    private Generator<String> stringLiteral() {
        return BasicGenerators.byteArray(0, context.config().maximumStringBytes())
                .map(payload -> {
                    var result = new StringBuilder(payload.length);
                    for (var value : payload) {
                        result.append(STRING_ALPHABET.charAt(
                                Byte.toUnsignedInt(value) % STRING_ALPHABET.length()));
                    }
                    return result.toString();
                });
    }
}
