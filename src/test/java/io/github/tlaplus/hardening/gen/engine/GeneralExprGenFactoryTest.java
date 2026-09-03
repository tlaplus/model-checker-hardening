package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
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
    void terminalsRotateOverVisibleBindingsAndThenTheClosedTerminal() {
        var fixture = fixture();
        var outer = new ScopedName("outer", PrimitiveType.INT);
        var inner = new ScopedName("inner", PrimitiveType.INT);
        var draw = new Draw(new byte[] {99});

        var printed = draw.draw(fixture.context().withBinding(
                outer,
                fixture.context().withBinding(
                        inner,
                        innerDraw -> {
                            var cycle = new ArrayList<String>();
                            for (var index = 0; index < 7; index++) {
                                cycle.add(print(innerDraw.draw(
                                        fixture.factory().terminal(PrimitiveType.INT))));
                            }
                            return cycle;
                        })));

        // The innermost binding stays the first candidate, and the cycle repeats.
        assertEquals(
                List.of("inner", "outer", "0", "inner", "outer", "0", "inner"), printed);
        assertEquals(1, draw.remaining(), "terminal consumed bytes");
    }

    /**
     * The property the rotation exists for. Returning the innermost binding every time made
     * every starved leaf of a type the same name, so same-type siblings collapsed into
     * tautologies such as {@code x = x}, which a model checker folds away before reaching
     * anything worth testing.
     */
    @Test
    void siblingTerminalsOfOneTypeDiffer() {
        var fixture = fixture();
        var only = new ScopedName("bound", PrimitiveType.INT);

        var printed = new Draw(new byte[0]).draw(fixture.context().withBinding(
                only,
                draw -> List.of(
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.INT))),
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.INT))))));

        assertNotEquals(printed.get(0), printed.get(1));
    }

    /** A rotation shared across types would let one type shift another's phase. */
    @Test
    void rotationsOfDifferentTypesAreIndependent() {
        var fixture = fixture();
        var number = new ScopedName("number", PrimitiveType.INT);
        var text = new ScopedName("text", PrimitiveType.STRING);

        var printed = new Draw(new byte[0]).draw(fixture.context().withBindings(
                List.of(number, text),
                draw -> List.of(
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.INT))),
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.STRING))),
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.INT))))));

        assertEquals(List.of("number", "text", "0"), printed);
    }

    @Test
    void terminalsIgnoreBindingsOfOtherTypesAndOperatorBindings() {
        var fixture = fixture();
        var otherType = new ScopedName("text", PrimitiveType.STRING);
        var operator = new ScopedName(
                "Op", new OperatorType(List.of(), PrimitiveType.INT));

        var printed = new Draw(new byte[0]).draw(fixture.context().withBindings(
                List.of(otherType, operator),
                draw -> print(draw.draw(fixture.factory().terminal(PrimitiveType.INT)))));

        assertEquals("0", printed);
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
