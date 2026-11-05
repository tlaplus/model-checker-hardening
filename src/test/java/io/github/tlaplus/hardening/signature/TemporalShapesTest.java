package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.common.ExprEdge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class TemporalShapesTest {
    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    @Test
    void countsNestedOperatorsThatReadTheState() {
        var x = builder.name("x", TlaTypes.INT);
        // [](<>(x > 0)) /\ [](<>(x > 0))
        var infinitelyOften = builder.always(builder.eventually(builder.gt(x, builder.integer(0))));
        var prop = builder.decl("Prop", builder.and(infinitelyOften, TlaExpressions.deepCopy(infinitelyOften)));

        var shapes = TemporalShapes.of(module(prop), List.of("Prop"));

        assertEquals(
                Map.of(
                        new ExprEdge(TemporalShapes.PROPERTY, "GLOBALLY"), 2L,
                        new ExprEdge("GLOBALLY", "EVENTUALLY"), 2L),
                shapes);
    }

    @Test
    void ignoresOperatorsOverConstants() {
        // [](<>TRUE) /\ <>(x > 0 /\ []TRUE): only the outer <> reads the state, and the [] under it
        // does not, so it is no shape and does not enclose anything.
        var x = builder.name("x", TlaTypes.INT);
        var prop = builder.decl("Prop", builder.and(
                builder.always(builder.eventually(builder.bool(true))),
                builder.eventually(builder.and(builder.gt(x, builder.integer(0)), builder.always(builder.bool(true))))));

        assertEquals(
                Map.of(new ExprEdge(TemporalShapes.PROPERTY, "EVENTUALLY"), 1L),
                TemporalShapes.of(module(prop), List.of("Prop")));
    }

    @Test
    void followsDefinitionsLabelsAndLets() {
        // Positive == x > 0; Prop == lab :: [](LET P == Positive IN <>P); Fairness == TRUE
        var positive = builder.decl("Positive", builder.gt(builder.name("x", TlaTypes.INT), builder.integer(0)));
        var local = builder.decl("P", builder.name("Positive", TlaTypes.BOOL));
        var prop = builder.decl("Prop", builder.label(
                builder.always(builder.letIn(builder.eventually(builder.name("P", TlaTypes.BOOL)), local)), "lab"));
        var fairness = builder.decl("Fairness", builder.bool(true));

        assertEquals(
                Map.of(
                        new ExprEdge(TemporalShapes.PROPERTY, "GLOBALLY"), 1L,
                        new ExprEdge("GLOBALLY", "EVENTUALLY"), 1L),
                TemporalShapes.of(module(positive, prop, fairness), List.of("Prop", "Fairness", "PropC")));
    }

    private static TlaModule module(TlaDecl... definitions) {
        var declarations = new ArrayList<TlaDecl>();
        declarations.add(TlaDeclarations.variable("x", TlaTypes.INT));
        declarations.addAll(List.of(definitions));
        return TlaModules.create("M", declarations);
    }
}
