package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import java.math.BigInteger;
import org.apalache_mc.tla.jir.TlaBuilderExpr;

/** Constructs integer-valued expression generators. */
final class IntegerExprGenFactory extends AbstractExprGenFactory {
    IntegerExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected integer form. */
    Generator<TlaBuilderExpr> mkGen(IntegerExpressionKind kind, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case INTEGER_LITERAL -> builder().integer(draw.draw(integerLiteral()));
                case PLUS -> draw.draw(integerBinary(remainingDepth, Arithmetic.PLUS));
                case MINUS -> draw.draw(integerBinary(remainingDepth, Arithmetic.MINUS));
                case UNARY_MINUS -> builder().uminus(
                        draw.draw(expression(PrimitiveType.INT, nextDepth)));
                case MULTIPLY ->
                    draw.draw(integerBinary(remainingDepth, Arithmetic.MULTIPLY));
                case DIVIDE -> draw.draw(integerBinary(remainingDepth, Arithmetic.DIVIDE));
                case MODULO -> draw.draw(integerBinary(remainingDepth, Arithmetic.MODULO));
                case EXPONENT ->
                    draw.draw(integerBinary(remainingDepth, Arithmetic.EXPONENT));
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

    /** Returns a generator of the selected binary integer operation. */
    private Generator<TlaBuilderExpr> integerBinary(
            int remainingDepth, Arithmetic operation) {
        return draw -> {
            var left = draw.draw(expression(PrimitiveType.INT, remainingDepth - 1));
            var right = draw.draw(expression(PrimitiveType.INT, remainingDepth - 1));
            return switch (operation) {
                case PLUS -> builder().plus(left, right);
                case MINUS -> builder().minus(left, right);
                case MULTIPLY -> builder().mult(left, right);
                case DIVIDE -> builder().div(left, right);
                case MODULO -> builder().mod(left, right);
                case EXPONENT -> builder().exp(left, right);
            };
        };
    }

    /** Returns a generator that decodes a terminated two's-complement integer payload. */
    private Generator<BigInteger> integerLiteral() {
        return BasicGenerators.byteArray(0, context.config().maximumIntegerBytes())
                .map(payload -> payload.length == 0
                        ? BigInteger.ZERO
                        : new BigInteger(payload));
    }

    /** Binary arithmetic operations. */
    private enum Arithmetic {
        PLUS,
        MINUS,
        MULTIPLY,
        DIVIDE,
        MODULO,
        EXPONENT
    }
}
