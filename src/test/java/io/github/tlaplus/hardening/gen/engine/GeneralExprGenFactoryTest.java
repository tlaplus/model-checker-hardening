package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneralExprGenFactoryTest {
    @Test
    void terminalsAreClosedByteFreeExpressionsForEveryTypeKind() {
        var types = List.<IrType>of(
                PrimitiveType.BOOL,
                PrimitiveType.INT,
                PrimitiveType.STRING,
                new ConstantType("MODEL"),
                new SetType(PrimitiveType.BOOL),
                new SequenceType(PrimitiveType.INT),
                new FunctionType(PrimitiveType.BOOL, PrimitiveType.INT),
                new TupleType(List.of(PrimitiveType.BOOL, PrimitiveType.INT)),
                new RecordType(List.of(new Field("field", PrimitiveType.BOOL))),
                new VariantType(List.of(new Field("Tag", PrimitiveType.INT))),
                new OperatorType(List.of(PrimitiveType.BOOL), PrimitiveType.INT));

        for (var type : types) {
            var fixture = fixture();
            var draw = new Draw(new byte[] {99});
            var expression = draw.draw(fixture.factory().terminal(type));

            assertEquals(1, draw.remaining(), () -> "terminal consumed bytes for " + type);
            assertFalse(print(expression).isEmpty());
        }
    }

    @Test
    void modelValueTerminalPrintsAsAQuotedIrValue() {
        var fixture = fixture();
        var expression = new Draw(new byte[0]).draw(
                fixture.factory().terminal(new ConstantType("MODEL")));

        assertEquals(
                "\"default_OF_MODEL\"",
                print(expression));
    }

    private Fixture fixture() {
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        var otherFactory = new OtherExprGenFactory(context, typeFactory, expressionFactory);
        return new Fixture(
                context,
                new GeneralExprGenFactory(
                        context, typeFactory, expressionFactory, otherFactory));
    }

    private String print(TlaEx expression) {
        var buffer = new StringWriter();
        var printWriter = new PrintWriter(buffer);
        new PrettyWriter(printWriter, new TextLayout(80, 2), new TlaDeclAnnotator())
                .write(expression);
        printWriter.flush();
        return buffer.toString();
    }

    private record Fixture(GenerationContext context, GeneralExprGenFactory factory) {}
}
