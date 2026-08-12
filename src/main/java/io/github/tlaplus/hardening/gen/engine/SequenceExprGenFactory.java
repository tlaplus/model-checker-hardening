package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import org.apalache_mc.tla.jir.TlaBuilderExpr;

/** Constructs sequence-valued expression generators. */
final class SequenceExprGenFactory extends AbstractExprGenFactory {
    SequenceExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected sequence form. */
    Generator<TlaBuilderExpr> mkGen(
            SequenceExpressionKind kind,
            SequenceType type,
            int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case EMPTY_SEQUENCE -> builder().emptySeq(type.element().toTlaType());
                case SEQUENCE_LITERAL -> builder().seq(BuilderArrays.expressions(draw.draw(
                        BasicGenerators.listOf(
                                expression(type.element(), nextDepth),
                                1,
                                context.config().maximumCollectionSize()))));
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
