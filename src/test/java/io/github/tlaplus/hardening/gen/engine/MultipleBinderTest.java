package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.containsOperator;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.junit.jupiter.api.Test;

/** Function constructors and set maps that bind several names in one construct. */
class MultipleBinderTest {
    private static final FunctionType PAIR_FUNCTION = new FunctionType(
            new TupleType(List.of(PrimitiveType.BOOL, PrimitiveType.INT)), PrimitiveType.BOOL);

    @Test
    void aFunctionOfATupleMayBindOneNamePerComponent() {
        assertForm("[ arg0 \\in {}, arg1 \\in {} |-> arg0 ]",
                OtherExpressionKind.FUNCTION_DEFINITION, PAIR_FUNCTION, 1, 42);
        assertForm("[ arg0 \\in {} |-> FALSE ]",
                OtherExpressionKind.FUNCTION_DEFINITION, PAIR_FUNCTION, 0, 42);
    }

    @Test
    void aFunctionOfAnythingElseSpendsNoMarkerOnTheChoice() {
        assertForm("[ arg0 \\in {} |-> arg0 ]", OtherExpressionKind.FUNCTION_DEFINITION,
                new FunctionType(PrimitiveType.BOOL, PrimitiveType.BOOL), 42);
        assertForm("[ arg0 \\in {} |-> FALSE ]", OtherExpressionKind.FUNCTION_DEFINITION,
                new FunctionType(new TupleType(List.of(PrimitiveType.INT)), PrimitiveType.BOOL), 42);
    }

    @Test
    void aSetMapBindsATerminatedListOfNames() {
        // Source types Boolean then integer, then the terminating marker.
        assertForm("{ mapped0: mapped0 \\in {}, mapped1 \\in {} }",
                SetExpressionKind.SET_MAP, new SetType(PrimitiveType.BOOL), 0, 1, 1, 0, 42);
    }

    @Test
    void labelsDeclareEveryNameOfTheConstructOnlyInsideItsBody() {
        var config = IrGenerationConfig.defaults().withFormWeights(Map.of(
                GeneralExpressionKind.LABEL, IrGenerationConfig.MAXIMUM_FORM_WEIGHT));
        var random = new Random(0xb1d5L);
        var multiple = 0;
        for (var sample = 0; sample < 300; sample++) {
            var context = new GenerationContext(config);
            var expressions = new IrExprGenFactory(context, new IrTypeGenFactory(context));
            var input = new byte[32 + random.nextInt(256)];
            random.nextBytes(input);
            var constructor = new Draw(input).draw(
                    expressions.mkGen(SetExpressionKind.SET_MAP, new SetType(PrimitiveType.BOOL), 5));
            TlaIrTestSupport.assertLabelParameters(constructor);
            var arguments = TlaExpressions.arguments((OperEx) constructor);
            if (arguments.size() > 3 && containsOperator(arguments.getFirst(), TlaOperators.LABEL)) {
                multiple++;
            }
        }
        assertTrue(multiple > 0, "no labelled body of a multiple-binder set map was generated");
    }

    private void assertForm(String expected, ExpressionKind kind, IrType type, int... input) {
        var bytes = new byte[input.length];
        for (var index = 0; index < input.length; index++) {
            bytes[index] = (byte) input[index];
        }
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var expressions = new IrExprGenFactory(context, new IrTypeGenFactory(context));
        var draw = new Draw(bytes);
        assertEquals(expected, print(draw.draw(expressions.mkGen(kind, type, 1))));
        assertEquals(1, draw.remaining(), () -> "unexpected consumption for " + expected);
    }
}
