package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class IrTypeGenFactoryTest {
    @Test
    void anyTypeFactoryIsDeferredAndSharesTheSuppliedCursor() {
        var factory = factory();
        var draw = new Draw(new byte[] {6, 1, 2, 99});

        var generator = factory.anyType();

        assertEquals(4, draw.remaining());
        assertEquals(
                new FunctionType(PrimitiveType.INT, PrimitiveType.STRING),
                draw.draw(generator));
        assertEquals(1, draw.remaining());
    }

    @Test
    void valueTypeFactoryPreservesOuterToInnerFieldAllocation() {
        var factory = factory();
        var draw = new Draw(new byte[] {8, 8, 0, 0, 0});
        var expected = new RecordType(List.of(new Field(
                "field0",
                new RecordType(List.of(new Field("field1", PrimitiveType.BOOL))))));

        assertEquals(expected, draw.draw(factory.valueType()));
        assertEquals(0, draw.remaining());
    }

    /** Creates a type-generator factory with fresh per-test run state. */
    private IrTypeGenFactory factory() {
        return new IrTypeGenFactory(new GenerationContext(IrGenerationConfig.defaults()));
    }
}
