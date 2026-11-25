package io.github.tlaplus.hardening.gen.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import at.forsyte.apalache.tla.lir.TlaType1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
import java.math.BigInteger;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class TraceLiteralsTest {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Test
    void buildsScalars() {
        assertEquals("TRUE", print(NODES.booleanNode(true), TlaTypes.BOOL));
        assertEquals("-3", print(NODES.numberNode(-3), TlaTypes.INT));
        assertEquals("123456789012345678901234567890",
                print(NODES.numberNode(new BigInteger("123456789012345678901234567890")), TlaTypes.INT));
        assertEquals("\"a\"", print(NODES.textNode("a"), TlaTypes.STRING));
        assertEquals("\"m_OF_MODEL\"", print(NODES.textNode("m_OF_MODEL"), TlaTypes.constant("MODEL")));
    }

    @Test
    void buildsCollections() {
        assertEquals("{ 1, 2 }", print(NODES.arrayNode().add(1).add(2), TlaTypes.set(TlaTypes.INT)));
        assertEquals("{}", print(NODES.arrayNode(), TlaTypes.set(TlaTypes.INT)));
        assertEquals("<<1, 1>>", print(NODES.arrayNode().add(1).add(1), TlaTypes.sequence(TlaTypes.INT)));
        assertEquals("<<1, \"a\">>",
                print(NODES.arrayNode().add(1).add("a"), TlaTypes.tuple(TlaTypes.INT, TlaTypes.STRING)));
        assertEquals("[a |-> 1, b |-> TRUE]", print(NODES.arrayNode().add(1).add(true),
                TlaTypes.rowRecord(new NamedType("b", TlaTypes.BOOL), new NamedType("a", TlaTypes.INT))));
        assertEquals("Variant(\"A\", 2)", print(NODES.arrayNode().add("A").add(2),
                TlaTypes.variant(new NamedType("A", TlaTypes.INT), new NamedType("B", TlaTypes.BOOL))));
    }

    @Test
    void buildsAFunctionAsACaseOverItsDomain() {
        var pairs = NODES.arrayNode();
        pairs.addArray().add(1).add("x");
        pairs.addArray().add(2).add("y");
        pairs.addArray().add(3).add("z");

        assertEquals("[ traceKey0 \\in { 1, 2, 3 } |-> CASE traceKey0 = 1 -> \"x\" [] traceKey0 = 2 -> \"y\""
                        + " [] OTHER -> \"z\" ]",
                print(pairs, TlaTypes.function(TlaTypes.INT, TlaTypes.STRING)));
    }

    @Test
    void buildsAFunctionOfOneArgumentAndOneOfNone() {
        var single = NODES.arrayNode();
        single.addArray().add(1).add(true);
        var type = TlaTypes.function(TlaTypes.INT, TlaTypes.BOOL);

        assertEquals("[ traceKey0 \\in {1} |-> TRUE ]", print(single, type));
        assertEquals("[ traceKey0 \\in {} |-> FALSE ]", print(NODES.arrayNode(), type));
    }

    @Test
    void rejectsAValueOfAnotherType() {
        var literals = new TraceLiterals();
        for (var mismatch : new Object[][] {
            {NODES.numberNode(1), TlaTypes.BOOL},
            {NODES.textNode("a"), TlaTypes.INT},
            {NODES.textNode("m_OF_OTHER"), TlaTypes.constant("MODEL")},
            {NODES.arrayNode().add(1), TlaTypes.tuple(TlaTypes.INT, TlaTypes.INT)},
            {NODES.arrayNode().add("C").add(1), TlaTypes.variant(new NamedType("A", TlaTypes.INT))},
            {NODES.arrayNode().add(1), TlaTypes.function(TlaTypes.INT, TlaTypes.INT)},
        }) {
            assertThrows(InputRejectedException.class,
                    () -> literals.literal((JsonNode) mismatch[0], (TlaType1) mismatch[1]), mismatch[0] + " : " + mismatch[1]);
        }
    }

    private static String print(JsonNode value, TlaType1 type) {
        var literal = new TraceLiterals().literal(value, type);
        assertEquals(type, TlaTypes.typeOf(literal));
        return TlaIrTestSupport.print(literal);
    }
}
