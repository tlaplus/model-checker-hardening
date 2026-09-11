package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import org.apalache_mc.tla.jio.TlaJson;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class ApalacheIrJsonTest {
    @Test
    void preservesClosedTypesAndErasesNestedLabels() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var variantType = TlaTypes.variant(
                new NamedType("Tag0", TlaTypes.INT),
                new NamedType("Tag1", TlaTypes.BOOL));
        var expression = builder.tuple(
                builder.label(
                        builder.label(builder.emptySet(TlaTypes.INT), "inner"),
                        "outer"),
                builder.emptySeq(TlaTypes.BOOL),
                builder.variant("Tag0", builder.integer(0), variantType));

        var json = ApalacheIrJson.render(FuzzInputModule.create(expression)).replaceAll("\\s+", "");

        assertTrue(json.contains("\"name\":\"ApalacheIR\""), json);
        assertTrue(json.contains("\"type\":\"Set(Int)\""), json);
        assertTrue(json.contains("\"type\":\"Seq(Bool)\""), json);
        assertTrue(json.contains("\"type\":\"Tag0(Int)|Tag1(Bool)\""), json);
        assertFalse(json.contains("\"oper\":\"LABEL\""), json);
        assertFalse(json.contains("inner"), json);
        assertFalse(json.contains("outer"), json);
    }

    @Test
    void roundTripsThroughTheJavaJsonReader() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var expression = builder.tuple(
                builder.integer(0),
                builder.label(builder.bool(false), "label0"));
        var json = ApalacheIrJson.render(FuzzInputModule.create(expression));

        assertDoesNotThrow(() -> TlaJson.readModule(json));
    }
}
