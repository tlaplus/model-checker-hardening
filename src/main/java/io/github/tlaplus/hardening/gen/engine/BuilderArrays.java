package io.github.tlaplus.hardening.gen.engine;

import java.util.List;
import org.apalache_mc.tla.jir.ExceptUpdate;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.NamedExpression;
import org.apalache_mc.tla.jir.TlaBuilderExpr;

/** Array conversions for the checked builder's generic varargs APIs. */
final class BuilderArrays {
    private BuilderArrays() {}

    static TlaBuilderExpr[] expressions(List<TlaBuilderExpr> expressions) {
        return expressions.toArray(TlaBuilderExpr[]::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static ExpressionPair<TlaBuilderExpr>[] pairs(
            List<? extends ExpressionPair<TlaBuilderExpr>> pairs) {
        return pairs.toArray(ExpressionPair[]::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static NamedExpression<TlaBuilderExpr>[] named(
            List<? extends NamedExpression<TlaBuilderExpr>> expressions) {
        return expressions.toArray(NamedExpression[]::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static ExceptUpdate<TlaBuilderExpr>[] updates(
            List<? extends ExceptUpdate<TlaBuilderExpr>> updates) {
        return updates.toArray(ExceptUpdate[]::new);
    }
}
