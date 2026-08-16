package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.IntT1$;
import at.forsyte.apalache.tla.lir.TlaEx;
import org.apalache_mc.tla.jir.NamedExpression;
import org.apalache_mc.tla.jir.TlaBuilderExpr;
import org.apalache_mc.tla.jir.TlaCheckedBuilder;
import org.junit.jupiter.api.Test;

class CollectionRichnessTest {
    private final TlaCheckedBuilder builder = new TlaCheckedBuilder();

    @Test
    void scoresEmptyAndFlatCollectionLiterals() {
        assertEquals(0.0, score(builder.emptySet(IntT1$.MODULE$), 2.0));
        assertEquals(
                3.0,
                score(builder.enumSet(
                        builder.integer(1), builder.integer(2), builder.integer(3)), 2.0));
        assertEquals(2.0, score(builder.seq(builder.integer(1), builder.integer(2)), 2.0));
        assertEquals(3.0, score(builder.tuple(
                builder.integer(1), builder.integer(2), builder.integer(3)), 2.0));
        assertEquals(
                2.0,
                score(builder.record(
                        new NamedExpression<>("a", builder.integer(1)),
                        new NamedExpression<>("b", builder.integer(2))), 2.0));
    }

    @Test
    void weightsOnlyCollectionNestingAndSumsSiblingCollections() {
        var nested = builder.tuple(
                builder.enumSet(builder.integer(1)),
                builder.seq(builder.integer(1), builder.integer(2)));
        var wrapped = builder.eql(nested, nested);

        // Each outer tuple contributes 2. Its nested set and sequence contribute
        // (1 + 2) * 2 at collection level one. Equality adds no nesting level.
        assertEquals(16.0, score(wrapped, 2.0));
    }

    @Test
    void usesTheConfiguredNestingBaseAndSaturatesOverflow() {
        var nested = builder.tuple(builder.seq(builder.integer(1)));

        assertEquals(4.0, score(nested, 3.0));
        assertEquals(Double.MAX_VALUE, score(nested, Double.MAX_VALUE));
    }

    private double score(TlaBuilderExpr expression, double nestingBase) {
        TlaEx built = builder.build(expression);
        return CollectionRichness.score(built, nestingBase);
    }
}
