package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fixed vectors pin both the action IR and the cursor protocol, independently of expressions. */
class ActionGenFactoryTest {
    private final GenerationContext context = new GenerationContext(IrGenerationConfig.defaults());
    private final IrTypeGenFactory types = new IrTypeGenFactory(context);
    private final List<ScopedName> variables = List.of(
            ScopedName.stateVariable("x", PrimitiveType.BOOL),
            ScopedName.stateVariable("y", PrimitiveType.BOOL));
    private final ActionShapeGenFactory shapes = new ActionShapeGenFactory(context, types,
            new IrExprGenFactory(context, types));
    private VisibleActionOperators visible = VisibleActionOperators.EMPTY;
    private final ActionGenFactory factory = new ActionGenFactory(context, types,
            new IrExprGenFactory(context, types), variables,
            ScopedName.stateVariable("step", PrimitiveType.INT));

    @Test
    void leaf() throws Exception {
        assertShape("x' = FALSE /\\ UNCHANGED y", 3, 1, 0, 0, 0, 0, 42);
    }

    @Test
    void conjunction() throws Exception {
        assertShape("x' = FALSE /\\ UNCHANGED y", 3, 1,
                1, 0, 1, 0, 0, 0, 0, 0, 0, 42);
    }

    @Test
    void disjunction() throws Exception {
        assertShape("(x' = FALSE /\\ UNCHANGED y) \\/ (x' = FALSE /\\ UNCHANGED y)", 3, 1,
                1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 42);
    }

    @Test
    void conditional() throws Exception {
        assertShape("IF FALSE THEN x' = FALSE /\\ UNCHANGED y ELSE x' = FALSE /\\ UNCHANGED y", 3, 1,
                1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 42);
    }

    @Test
    void call() throws Exception {
        var draw = bytes(1, 1, 1, 0, 0, 0, 0, 0, 0, 42);
        var operators = draw.draw(factory.actionOperators(0));
        assertEquals(1, operators.size());
        assertEquals("x' = FALSE /\\ UNCHANGED y", print(operators.getFirst().generated().declaration().body()));
        assertEquals(1, draw.remaining());
        visible = new VisibleActionOperators(operators);
        assertShape("Act0", 3, 1, 1, 1, 1, 1, 42);
    }

    @Test
    void operatorsSeeOnlyTheirExplicitPrefix() {
        var draw = bytes(1, 1, 1, 0, 0, 0, 0, 0,
                1, 1, 1, 0, 1, 1, 1, 1, 42);
        var operators = draw.draw(factory.actionOperators(0));
        assertEquals(2, operators.size());
        assertEquals("Act0", print(operators.get(1).generated().declaration().body()));
        assertEquals(1, draw.remaining());
        assertEquals(List.of(), bytes(0).draw(factory.actionOperators(0)));
    }

    @Test
    void aDeferredNextDoesNotAcquireVisibilityFromLaterFactoryCalls() {
        var next = factory.nextAction(0, VisibleActionOperators.EMPTY);
        bytes(1, 1, 1, 0, 0, 0, 0, 0, 0).draw(factory.actionOperators(0));
        var draw = bytes(0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 42);
        assertEquals("(x' = FALSE /\\ UNCHANGED y /\\ step' = step + 1)", print(draw.draw(next)));
        assertEquals(1, draw.remaining());
    }

    @Test
    void unavailableCallFallsBackWithoutASelectionByte() throws Exception {
        assertShape("x' = FALSE /\\ UNCHANGED y", 3, 1, 1, 1, 1, 1, 0, 0, 0, 42);
    }

    @Test
    void depthFallbackSkipsKindMarkers() throws Exception {
        assertShape("x' = FALSE /\\ UNCHANGED y", 0, 1, 0, 0, 0, 42);
    }

    @Test
    void budgetFallbackSkipsKindMarkers() throws Exception {
        while (context.consumeNode()) { }
        assertShape("x' = FALSE /\\ UNCHANGED y", 3, 1, 0, 0, 0, 42);
    }

    @Test
    void exhaustionFallbackConsumesNoSyntheticBytes() throws Exception {
        assertShape("x' = FALSE /\\ UNCHANGED y", 3, 0);
    }

    @Test
    void decoderOrderAndGeometricMarkersArePinned() throws Exception {
        var kind = ActionShapeGenFactory.ShapeKind.class;
        assertEquals(List.of("LEAF", "CONJUNCTION", "DISJUNCTION", "ITE", "CALL"),
                Arrays.stream(kind.getEnumConstants()).map(Object::toString).toList());
        var expected = List.of("LEAF", "CONJUNCTION", "DISJUNCTION", "ITE", "CALL");
        for (int bits = 0; bits < 16; bits++) {
            var input = new int[] {bits & 1, (bits >> 1) & 1, (bits >> 2) & 1, (bits >> 3) & 1, 42};
            int index = 0;
            while (index < 4 && input[index] == 1) index++;
            var draw = bytes(input);
            assertEquals(expected.get(index), ActionShapeGenFactory.drawShapeKind(draw).toString());
            assertEquals(5 - Math.min(index + 1, 4), draw.remaining());
        }
    }

    private void assertShape(String expected, int depth, int remaining, int... input) {
        var generator = shapes.shape(new ActionShapeGenFactory.Request(variables, depth, 0,
                ActionShapeGenFactory.AssignmentRequirement.REQUIRED), visible);
        var draw = bytes(input);
        var conjuncts = draw.draw(generator);
        var shape = conjuncts.size() == 1 ? conjuncts.getFirst()
                : context.builder().and(BuilderArrays.expressions(conjuncts));
        assertEquals(expected, print(shape));
        assertEquals(remaining, draw.remaining());
    }

    private static Draw bytes(int... input) {
        var bytes = new byte[input.length];
        for (int i = 0; i < input.length; i++) bytes[i] = (byte) input[i];
        return new Draw(bytes);
    }
}
