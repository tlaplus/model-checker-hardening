package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import static io.github.tlaplus.hardening.common.ScalaCollections.*;

/** Instantiates one type scheme and draws its value arguments under the ordinary expression budget. */
final class CustomExprGenFactory {
    private final GenerationContext context;
    private final IrTypeGenFactory types;
    private final IrExprGenFactory expressions;

    CustomExprGenFactory(GenerationContext context, IrTypeGenFactory types, IrExprGenFactory expressions) {
        this.context = context;
        this.types = types;
        this.expressions = expressions;
    }

    Generator<TlaEx> mkGen(CustomExpressionKind kind, IrType result, int remainingDepth) {
        return draw -> {
            var plan = kind.plan(context.config(), result).orElseThrow();
            var signature = (OperT1) draw.draw(plan.generator(context, types));
            var arguments = list(signature.args()).stream()
                    .map(type -> draw.draw(expressions.mkGen(ImportedTypes.from(type), remainingDepth - 1)))
                    .toArray(TlaEx[]::new);
            var name = context.config().library().get(kind.id()).name();
            return context.builder().operApply(context.builder().name(name, signature), arguments);
        };
    }
}
