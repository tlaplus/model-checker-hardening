package io.github.tlaplus.hardening.gen.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import at.forsyte.apalache.tla.lir.TlaType1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class ItfTracesTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readsStatesInDeclarationOrderAndTheEnding() {
        var trace = ItfTraces.read("""
                {"ending": "loop", "loop": 0, "states": [{"y": true, "x": 1}, {"x": {"#bigint": "1"}, "y": true}]}
                """, List.of(TlaDeclarations.variable("x", TlaTypes.INT), TlaDeclarations.variable("y", TlaTypes.BOOL)));

        assertEquals(new TraceEnding.Loop(0), trace.ending());
        assertEquals(List.of(json("1"), json("true")), trace.states().get(0));
        assertEquals(trace.states().get(0), trace.states().get(1));
    }

    /** Apalache writes an integer as a bignum string, TLC as a number; both read the same. */
    @Test
    void readsIntegersInEitherForm() {
        assertEquals(json("7"), value("{\"#bigint\": \"7\"}", TlaTypes.INT));
        assertEquals(json("-7"), value("-7", TlaTypes.INT));
        assertEquals(json("123456789012345678901234567890"),
                value("{\"#bigint\": \"123456789012345678901234567890\"}", TlaTypes.INT));
    }

    @Test
    void ordersASetByTheBytesOfItsElements() {
        assertEquals(json("[1, 2, 10]"), value("{\"#set\": [10, 2, 1, 2]}", TlaTypes.set(TlaTypes.INT)));
    }

    /** TLC holds a sequence as a tuple or as a function on 1..n; Apalache writes it as an array. */
    @Test
    void readsASequenceInEveryForm() {
        var type = TlaTypes.sequence(TlaTypes.STRING);
        assertEquals(json("[\"a\", \"b\"]"), value("[\"a\", \"b\"]", type));
        assertEquals(json("[\"a\", \"b\"]"), value("{\"#tup\": [\"a\", \"b\"]}", type));
        assertEquals(json("[\"a\", \"b\"]"), value("{\"#map\": [[2, \"b\"], [1, \"a\"]]}", type));
        assertThrows(IllegalArgumentException.class, () -> value("{\"#map\": [[2, \"b\"]]}", type));
    }

    @Test
    void readsAFunctionAsPairsOrderedByKey() {
        var type = TlaTypes.function(TlaTypes.INT, TlaTypes.BOOL);
        assertEquals(json("[[1, true], [3, false]]"), value("{\"#map\": [[3, false], [1, true]]}", type));
        assertEquals(json("[[1, true], [2, false]]"), value("[true, false]", type));
        assertEquals(json("[[\"a\", 1]]"),
                value("{\"a\": 1}", TlaTypes.function(TlaTypes.STRING, TlaTypes.INT)));
    }

    @Test
    void readsATupleAndARecordInFieldOrder() {
        assertEquals(json("[1, \"a\"]"), value("{\"#tup\": [1, \"a\"]}", TlaTypes.tuple(TlaTypes.INT, TlaTypes.STRING)));
        assertEquals(json("[1, true]"), value("{\"b\": true, \"a\": 1}",
                TlaTypes.rowRecord(new NamedType("a", TlaTypes.INT), new NamedType("b", TlaTypes.BOOL))));
    }

    /** Apalache writes a variant as a tag and a value; TLC's Variant(t, v) is the function t :> v. */
    @Test
    void readsAVariantInEitherForm() {
        var type = TlaTypes.variant(new NamedType("A", TlaTypes.INT), new NamedType("B", TlaTypes.BOOL));
        assertEquals(json("[\"A\", 3]"), value("{\"tag\": \"A\", \"value\": {\"#bigint\": \"3\"}}", type));
        assertEquals(json("[\"A\", 3]"), value("{\"#map\": [[\"A\", 3]]}", type));
        assertEquals(json("[\"B\", true]"), value("{\"B\": true}", type));
        assertThrows(IllegalArgumentException.class, () -> value("{\"C\": true}", type));
    }

    @Test
    void rejectsAMissingVariableOrAValueOfAnotherType() {
        var variables = List.of(TlaDeclarations.variable("x", TlaTypes.INT));
        assertThrows(IllegalArgumentException.class,
                () -> ItfTraces.read("{\"ending\": \"invariant\", \"states\": [{\"y\": 1}]}", variables));
        assertThrows(IllegalArgumentException.class,
                () -> ItfTraces.read("{\"ending\": \"invariant\", \"states\": [{\"x\": \"1\"}]}", variables));
        assertThrows(IllegalArgumentException.class,
                () -> ItfTraces.read("{\"ending\": \"final\", \"states\": [{\"x\": 1}]}", variables));
    }

    private static JsonNode value(String itf, TlaType1 type) {
        return ItfTraces.value(json(itf), type);
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
