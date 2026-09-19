package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.common.ExprEdge;
import java.util.List;
import java.util.Map;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class IrExprCountsTest {
    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    @Test
    void countsConstructsOfReachableDefinitionsOnceAndSkipsLabels() {
        var helper = builder.decl(
                "Helper", builder.label(builder.plus(builder.integer(1), builder.integer(2)), "lab"));
        var unused = builder.decl("Unused", builder.plus(builder.integer(3), builder.integer(4)));
        var local = builder.decl("Local", builder.minus(builder.integer(5), builder.integer(6)));
        var inv = builder.decl(
                "Inv",
                builder.and(
                        builder.eql(builder.name("Helper", TlaTypes.INT), builder.name("Helper", TlaTypes.INT)),
                        builder.letIn(builder.bool(true), local)));
        var module = TlaModules.create("M", List.of(helper, unused, inv));

        var counts = IrExprCounts.evaluated(module, List.of("Inv"));

        // Helper is walked once although referenced twice; Unused is never reached; the LET
        // definition is walked although nothing references it; the label is transparent; the
        // names Helper are not constructs.
        assertEquals(
                Map.of(
                        "AND", 1L,
                        "EQ", 1L,
                        "PLUS", 1L,
                        "MINUS", 1L,
                        IrExprCounts.LET_IN, 1L,
                        "TlaInt", 4L,
                        "TlaBool", 1L),
                counts.exprs());
        assertEquals(IrTree.evaluatedSubexpressions(module, List.of("Inv")).size(), counts.nodes());
        // Edges skip names: the bodies of Helper and Local have no parent, and the arguments of EQ
        // are names. The label between Helper and PLUS is transparent.
        assertEquals(
                Map.of(
                        new ExprEdge("AND", "EQ"), 1L,
                        new ExprEdge("AND", IrExprCounts.LET_IN), 1L,
                        new ExprEdge(IrExprCounts.LET_IN, "TlaBool"), 1L,
                        new ExprEdge("PLUS", "TlaInt"), 2L,
                        new ExprEdge("MINUS", "TlaInt"), 2L),
                counts.edges());
    }

    @Test
    void namesLiteralsByTheKindOfTheirValue() {
        var inv = builder.decl(
                "Inv",
                builder.and(
                        builder.in(builder.integer(1), builder.natSet()),
                        builder.in(builder.str("a"), builder.stringSet()),
                        builder.in(builder.bool(true), builder.booleanSet()),
                        builder.in(builder.integer(-1), builder.intSet())));

        var counts = IrExprCounts.evaluated(TlaModules.create("M", List.of(inv)), List.of("Inv"));

        assertEquals(
                Map.of(
                        "AND", 1L,
                        "SET_IN", 4L,
                        "TlaInt", 2L,
                        "TlaNatSet", 1L,
                        "TlaStr", 1L,
                        "TlaStrSet", 1L,
                        "TlaBool", 1L,
                        "TlaBoolSet", 1L,
                        "TlaIntSet", 1L),
                counts.exprs());
    }

    @Test
    void rejectsAModuleWithoutARoot() {
        var module = TlaModules.create("M", List.of(builder.decl("Other", builder.bool(true))));

        assertThrows(
                IllegalArgumentException.class,
                () -> IrExprCounts.evaluated(module, List.of("Inv")));
    }
}
