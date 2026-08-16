package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;

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

    /** Returns the type-safe, scope-unchecked builder for this run. */
    protected final TlaTypedScopeUncheckedBuilder builder() {
        return context.builder();
    }

    /** Returns a recursive expression generator with the requested type and budget. */
    protected final Generator<TlaEx> expression(
            IrType type, int remainingDepth) {
        return expressionFactory.mkGen(type, remainingDepth);
    }
}
