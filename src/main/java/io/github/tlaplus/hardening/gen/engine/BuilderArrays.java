package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.List;
import org.apalache_mc.tla.jir.ExceptUpdate;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.NamedExpression;

/** Array conversions for the builder's generic varargs APIs. */
final class BuilderArrays {
    private BuilderArrays() {}

    static TlaEx[] expressions(List<TlaEx> expressions) {
        return expressions.toArray(TlaEx[]::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static ExpressionPair<TlaEx>[] pairs(
            List<? extends ExpressionPair<TlaEx>> pairs) {
        return pairs.toArray(ExpressionPair[]::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static NamedExpression<TlaEx>[] named(
            List<? extends NamedExpression<TlaEx>> expressions) {
        return expressions.toArray(NamedExpression[]::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static ExceptUpdate<TlaEx>[] updates(
            List<? extends ExceptUpdate<TlaEx>> updates) {
        return updates.toArray(ExceptUpdate[]::new);
    }
}
