package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionKindsTest {
    @Test
    void catalogContainsEveryFamilyConstantExactlyOnce() {
        var expectedSize = GeneralExpressionKind.values().length
                + BooleanExpressionKind.values().length
                + IntegerExpressionKind.values().length
                + SetExpressionKind.values().length
                + SequenceExpressionKind.values().length
                + OtherExpressionKind.values().length;

        assertEquals(expectedSize, ExpressionKinds.all().size());
        assertEquals(expectedSize, new HashSet<>(ExpressionKinds.all()).size());
        assertTrue(expectedSize <= 256);
    }

    @Test
    void everyRepresentativeTypeUsesOneByteForItsTerminalChoice() {
        var types = List.<IrType>of(
                PrimitiveType.BOOL,
                PrimitiveType.INT,
                PrimitiveType.STRING,
                new ConstantType("MODEL"),
                new SetType(PrimitiveType.BOOL),
                new SequenceType(PrimitiveType.BOOL),
                new FunctionType(PrimitiveType.BOOL, PrimitiveType.INT),
                new TupleType(List.of(PrimitiveType.BOOL, PrimitiveType.INT)),
                new RecordType(List.of(new Field("field", PrimitiveType.BOOL))),
                new VariantType(List.of(new Field("Tag", PrimitiveType.INT))),
                new OperatorType(List.of(PrimitiveType.BOOL), PrimitiveType.INT));

        for (var type : types) {
            var draw = new Draw(new byte[] {0, 99});
            var context = new GenerationContext(IrGenerationConfig.defaults());
            var typeFactory = new IrTypeGenFactory(context);
            var expressionFactory = new IrExprGenFactory(context, typeFactory);

            draw.draw(expressionFactory.mkGen(type, 1));

            assertEquals(1, draw.remaining(), () -> "unexpected consumption for " + type);
        }
    }

    @Test
    void constructingExpressionGeneratorsDoesNotSpendNodeBudgetOrBytes() {
        var draw = new Draw(new byte[] {0, 99});
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);

        for (var index = 0; index < 100; index++) {
            expressionFactory.mkGen(PrimitiveType.BOOL, 1);
        }

        assertEquals(2, draw.remaining());
        draw.draw(expressionFactory.mkGen(PrimitiveType.BOOL, 1));
        assertEquals(1, draw.remaining());
    }
}
