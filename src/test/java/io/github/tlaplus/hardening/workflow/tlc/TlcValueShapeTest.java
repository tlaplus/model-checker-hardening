package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.SetEnumValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;
import util.UniqueString;

class TlcValueShapeTest {
    @Test
    void countsNodesCardinalityAndNesting() {
        // [a |-> {1, 2}, b |-> <<3>>]: record, set, 1, 2, tuple, 3.
        var record = new RecordValue(
                new UniqueString[] {UniqueString.uniqueStringOf("a"), UniqueString.uniqueStringOf("b")},
                new Value[] {set(1, 2), new TupleValue(IntValue.gen(3))},
                false);
        var shape = new TlcValueShape(100);

        shape.add(record);
        shape.add(IntValue.gen(0));

        assertEquals(7, shape.nodes());
        assertEquals(2, shape.cardinality());
        assertEquals(2, shape.nesting());
        assertFalse(shape.saturated());
    }

    @Test
    void stopsAtTheNodeCap() {
        var shape = new TlcValueShape(3);

        shape.add(set(1, 2, 3, 4, 5));

        assertEquals(3, shape.nodes());
        assertEquals(5, shape.cardinality());
        assertTrue(shape.saturated());
    }

    private static SetEnumValue set(int... elements) {
        var values = new Value[elements.length];
        for (var index = 0; index < elements.length; index++) {
            values[index] = IntValue.gen(elements[index]);
        }
        return new SetEnumValue(values, false);
    }
}
