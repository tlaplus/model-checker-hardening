package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
import java.util.Set;
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

    @Test
    void structuralTypesRespectCategoryCapabilities() {
        var noSets = factoryIgnoring(ExpressionCategory.SET);

        assertFalse(noSets.isEnabled(new SetType(PrimitiveType.BOOL)));
        assertFalse(noSets.isEnabled(
                new FunctionType(PrimitiveType.BOOL, PrimitiveType.INT)));
        assertFalse(noSets.isEnabled(new TupleType(List.of(
                PrimitiveType.BOOL, new SetType(PrimitiveType.INT)))));
        assertTrue(noSets.isEnabled(new SequenceType(PrimitiveType.BOOL)));

        var noRecords = factoryIgnoring(ExpressionCategory.RECORD);
        assertFalse(noRecords.isEnabled(
                new RecordType(List.of(new Field("field", PrimitiveType.BOOL)))));
        assertTrue(noRecords.isEnabled(
                new TupleType(List.of(PrimitiveType.BOOL))));
    }

    @Test
    void generatedTypesNeverUseIgnoredCategories() {
        for (var category : ExpressionCategory.values()) {
            if (category == ExpressionCategory.CORE) {
                continue;
            }
            var factory = factoryIgnoring(category);
            for (var value = 0; value < 256; value++) {
                var input = new byte[] {(byte) value, 1, 2, 3, 4, 0};
                var type = new Draw(input).draw(factory.anyType());
                assertTrue(factory.isEnabled(type), () -> category + " allowed " + type);
            }
        }
    }

    @Test
    void anOperatorRootKeepsItsArgumentFreeDecoding() {
        // Operator last among the eleven kinds, an empty argument list, a Boolean result.
        var draw = new Draw(new byte[] {10, 0, 0, 99});
        assertEquals(new OperatorType(List.of(), PrimitiveType.BOOL), draw.draw(factory().anyType()));
        assertEquals(1, draw.remaining());
    }

    @Test
    void anOperatorParameterTakesAtLeastOneArgument() {
        // One parameter of operator kind: its mandatory Boolean argument, the argument-list
        // marker, an integer result, then the parameter-list marker.
        var draw = new Draw(new byte[] {1, 10, 0, 0, 1, 0, 99});
        assertEquals(
                List.of(new OperatorType(List.of(PrimitiveType.BOOL), PrimitiveType.INT)),
                draw.draw(factory().parameterTypes()));
        assertEquals(1, draw.remaining());

        var factory = factory();
        for (var value = 0; value < 256; value++) {
            var input = new byte[] {1, (byte) value, 1, 2, 0, 3, 0, 0};
            for (var type : new Draw(input).draw(factory.parameterTypes())) {
                assertTrue(!(type instanceof OperatorType operator) || !operator.arguments().isEmpty(),
                        () -> "nullary operator parameter " + type);
            }
        }
    }

    @Test
    void parametersAreValuesWhileOperatorsAreIgnored() {
        var factory = factoryIgnoring(ExpressionCategory.OPERATOR);
        for (var value = 0; value < 256; value++) {
            var input = new byte[] {1, (byte) value, 1, 2, 0, 3, 0, 0};
            for (var type : new Draw(input).draw(factory.parameterTypes())) {
                assertFalse(type instanceof OperatorType, () -> "operator parameter " + type);
            }
        }
    }

    /** Creates a type-generator factory with fresh per-test run state. */
    private IrTypeGenFactory factory() {
        return new IrTypeGenFactory(new GenerationContext(IrGenerationConfig.defaults()));
    }

    private IrTypeGenFactory factoryIgnoring(ExpressionCategory category) {
        var config = IrGenerationConfig.defaults().withIgnoredCategories(Set.of(category));
        return new IrTypeGenFactory(new GenerationContext(config));
    }
}
