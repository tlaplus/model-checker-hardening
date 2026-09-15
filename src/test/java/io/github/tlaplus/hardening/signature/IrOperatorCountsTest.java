package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class IrOperatorCountsTest {
    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    @Test
    void countsOperatorsOfReachableDefinitionsOnceAndSkipsLabels() {
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

        var counts = IrOperatorCounts.evaluated(module, List.of("Inv"));

        // Helper is walked once although referenced twice; Unused is never reached; the LET
        // definition is walked although nothing references it; the label is transparent.
        assertEquals(Map.of("AND", 1L, "EQ", 1L, "PLUS", 1L, "MINUS", 1L), counts.operators());
        assertEquals(IrTree.evaluatedSubexpressions(module, List.of("Inv")).size(), counts.nodes());
    }

    @Test
    void rejectsAModuleWithoutARoot() {
        var module = TlaModules.create("M", List.of(builder.decl("Other", builder.bool(true))));

        assertThrows(
                IllegalArgumentException.class,
                () -> IrOperatorCounts.evaluated(module, List.of("Inv")));
    }
}
