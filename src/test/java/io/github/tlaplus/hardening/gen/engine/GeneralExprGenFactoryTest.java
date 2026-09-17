package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.CollectionLimits;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionLimits;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
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
        var outer = ScopedName.binder("outer", PrimitiveType.INT);
        var inner = ScopedName.binder("inner", PrimitiveType.INT);
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
                List.of("inner", "outer", "1", "inner", "outer", "1", "inner"), printed);
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
        var only = ScopedName.binder("bound", PrimitiveType.INT);

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
        var number = ScopedName.binder("number", PrimitiveType.INT);
        var text = ScopedName.binder("text", PrimitiveType.STRING);

        var printed = new Draw(new byte[0]).draw(fixture.context().withBindings(
                List.of(number, text),
                draw -> List.of(
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.INT))),
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.STRING))),
                        print(draw.draw(fixture.factory().terminal(PrimitiveType.INT))))));

        assertEquals(List.of("number", "text", "1"), printed);
    }

    @Test
    void terminalsIgnoreBindingsOfOtherTypesAndOperatorBindings() {
        var fixture = fixture();
        var otherType = ScopedName.binder("text", PrimitiveType.STRING);
        var operator = ScopedName.definition(
                "Op", new OperatorType(List.of(), PrimitiveType.INT));

        var printed = new Draw(new byte[0]).draw(fixture.context().withBindings(
                List.of(otherType, operator),
                draw -> print(draw.draw(fixture.factory().terminal(PrimitiveType.INT)))));

        assertEquals("1", printed);
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

    @Test
    void collectionTerminalsHaveTheBaseSizeWithDistinctElements() {
        assertEquals("{1, 2, 3}", closedTerminal(3, 64, new SetType(PrimitiveType.INT)));
        assertEquals("<<\"1\", \"2\", \"3\">>", closedTerminal(3, 64, new SequenceType(PrimitiveType.STRING)));
        assertEquals("{TRUE, FALSE}", closedTerminal(3, 64, new SetType(PrimitiveType.BOOL)));
        assertEquals("{<<1, TRUE>>, <<2, FALSE>>, <<3, TRUE>>}",
                closedTerminal(3, 64, new SetType(new TupleType(List.of(PrimitiveType.INT, PrimitiveType.BOOL)))));
        var function = closedTerminal(3, 64, new FunctionType(PrimitiveType.INT, PrimitiveType.BOOL));
        assertTrue(function.contains("\\in {1, 2, 3} |-> FALSE"), function);
    }

    @Test
    void nestedCollectionTerminalsShareTheAtomBudget() {
        // Four inner sets under a budget of 8 leave each inner set 2 atoms.
        assertEquals("[ terminalArg0 \\in {1, 2, 3, 4} |-> {1, 2} ]",
                closedTerminal(4, 8, new FunctionType(PrimitiveType.INT, new SetType(PrimitiveType.INT))));
        assertEquals("{1, 2}", closedTerminal(4, 2, new SetType(PrimitiveType.INT)));
    }

    @Test
    void aZeroBaseSizeKeepsEmptyCollectionTerminals() {
        assertEquals("{}", closedTerminal(0, 64, new SetType(PrimitiveType.INT)));
        assertEquals("<<>>", closedTerminal(0, 64, new SequenceType(PrimitiveType.INT)));
    }

    private String closedTerminal(int baseSize, int atoms, IrType type) {
        var defaults = IrGenerationConfig.defaults();
        var limits = defaults.expressions();
        var fixture = fixture(defaults.withExpressionLimits(new ExpressionLimits(limits.maximumTypeDepth(),
                limits.maximumExpressionDepth(), limits.maximumNodes(),
                new CollectionLimits(limits.collections().maximumSize(), baseSize, 4, atoms),
                limits.maximumStringBytes(), limits.integers())));
        var draw = new Draw(new byte[] {99});
        var printed = print(draw.draw(fixture.factory().closedTerminal(type)));
        assertEquals(1, draw.remaining(), "a closed terminal consumed bytes");
        return printed.replaceAll("\\s+", " ").replace("{ ", "{").replace(" }", "}").trim();
    }

    private Fixture fixture() {
        return fixture(IrGenerationConfig.defaults());
    }

    private Fixture fixture(IrGenerationConfig config) {
        var context = new GenerationContext(config);
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        var otherFactory = new OtherExprGenFactory(context, typeFactory, expressionFactory);
        return new Fixture(
                context,
                new GeneralExprGenFactory(
                        context, typeFactory, expressionFactory, otherFactory));
    }

    private String print(TlaEx expression) {
        return TlaIrTestSupport.print(expression);
    }

    private record Fixture(GenerationContext context, GeneralExprGenFactory factory) {}
}
