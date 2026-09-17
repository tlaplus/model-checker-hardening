package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IntegerLiteralMode;
import java.math.BigInteger;

/** Constructs integer-valued expression generators. */
final class IntegerExprGenFactory extends AbstractExprGenFactory {
    IntegerExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected integer form. */
    Generator<TlaEx> mkGen(IntegerExpressionKind kind, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case INTEGER_LITERAL -> builder().integer(draw.draw(integerLiteral()));
                case PLUS -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::plus));
                case MINUS -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::minus));
                case UNARY_MINUS -> builder().uminus(
                        draw.draw(expression(PrimitiveType.INT, nextDepth)));
                case MULTIPLY -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::mult));
                case DIVIDE -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::div));
                case MODULO -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::mod));
                case EXPONENT -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::exp));
                case CARDINALITY -> {
                    var elementType = draw.draw(typeFactory.valueType());
                    yield builder().cardinality(
                            draw.draw(expression(new SetType(elementType), nextDepth)));
                }
                case LENGTH -> builder().len(draw.draw(expression(
                        new SequenceType(draw.draw(typeFactory.valueType())), nextDepth)));
            };
        };
    }

    /** Returns a generator of an integer literal in the configured {@link IntegerLiteralMode}. */
    private Generator<BigInteger> integerLiteral() {
        var limits = context.config().expressions().integers();
        Generator<BigInteger> wide = BasicGenerators.byteArray(0, limits.maximumBytes())
                .map(payload -> payload.length == 0 ? BigInteger.ZERO : new BigInteger(payload));
        Generator<BigInteger> small = draw -> BigInteger.valueOf(
                (long) limits.base() + draw.draw(BasicGenerators.offset(limits.spread())));
        Generator<BigInteger> boundary = draw -> BOUNDARIES[draw.drawIndex(BOUNDARIES.length, 1)].value();
        return switch (limits.literals()) {
            case WIDE -> wide;
            case SMALL -> draw -> draw.draw(draw.drawBoolean() ? wide : small);
            case BOUNDARY -> draw -> !draw.drawBoolean()
                    ? draw.draw(small)
                    : draw.draw(draw.drawBoolean() ? wide : boundary);
        };
    }

    private static final BoundaryInteger[] BOUNDARIES = BoundaryInteger.values();

}
