package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Generator;
import org.apalache_mc.tla.jir.TlaBuilderExpr;
import org.apalache_mc.tla.jir.TlaCheckedBuilder;

/** Shared access to the state and recursive factories used by expression generator families. */
abstract class AbstractExprGenFactory {
    protected final GenerationContext context;
    protected final IrTypeGenFactory typeFactory;
    protected final IrExprGenFactory expressionFactory;

    AbstractExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        this.context = context;
        this.typeFactory = typeFactory;
        this.expressionFactory = expressionFactory;
    }

    /** Returns the checked builder for this run. */
    protected final TlaCheckedBuilder builder() {
        return context.builder();
    }

    /** Returns a recursive expression generator with the requested type and budget. */
    protected final Generator<TlaBuilderExpr> expression(
            IrType type, int remainingDepth) {
        return expressionFactory.mkGen(type, remainingDepth);
    }
}
