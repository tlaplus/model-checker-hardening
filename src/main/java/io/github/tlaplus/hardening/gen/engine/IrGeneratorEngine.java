package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.Objects;

/**
 * Reusable coordinator for type-directed IR-expression generation.
 *
 * <p>The engine stores only immutable configuration. Every {@link #generate(Draw)} invocation
 * creates an independent type-safe builder, scope, resource counters, and fresh-name supply, so one
 * engine may be reused and invoked concurrently with distinct cursors.
 */
public final class IrGeneratorEngine {
    private final IrGenerationConfig config;

    /**
     * Creates a reusable engine with the supplied generation settings.
     *
     * @param config category exclusions and resource limits
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public IrGeneratorEngine(IrGenerationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Generates one type-correct value expression from the current position of {@code draw}.
     *
     * @param draw cursor supplying every generation choice
     * @return generated Apalache IR expression
     * @throws NullPointerException if {@code draw} is {@code null}
     * @throws InputRejectedException if the decoded root is an operator type or the decoded
     *     choices reach another expected semantic dead end
     * @throws RuntimeException if the builder rejects an internally constructed expression
     */
    public TlaEx generate(Draw draw) {
        Objects.requireNonNull(draw, "draw");
        var context = new GenerationContext(config);
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        var resultType = draw.draw(typeFactory.anyType());
        if (resultType instanceof OperatorType) {
            throw new InputRejectedException(
                    "root expression has operator type; TLA+ operators are not values");
        }
        return draw.draw(expressionFactory.mkGen(
                resultType, config.maximumExpressionDepth()));
    }
}
