package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;

/** Constructs sequence-valued expression generators. */
final class SequenceExprGenFactory extends AbstractExprGenFactory {
    SequenceExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected sequence form. */
    Generator<TlaEx> mkGen(
            SequenceExpressionKind kind,
            SequenceType type,
            int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case EMPTY_SEQUENCE -> builder().emptySeq(type.element().toTlaType());
                case SEQUENCE_LITERAL -> builder().seq(
                        draw.draw(operands(type.element(), nextDepth)));
                case APPEND -> builder().append(
                        draw.draw(expression(type, nextDepth)),
                        draw.draw(expression(type.element(), nextDepth)));
                case CONCATENATE -> builder().concat(
                        draw.draw(expression(type, nextDepth)),
                        draw.draw(expression(type, nextDepth)));
                case TAIL -> builder().tail(draw.draw(expression(type, nextDepth)));
                case SUBSEQUENCE -> builder().subSeq(
                        draw.draw(expression(type, nextDepth)),
                        draw.draw(expression(PrimitiveType.INT, nextDepth)),
                        draw.draw(expression(PrimitiveType.INT, nextDepth)));
            };
        };
    }
}
