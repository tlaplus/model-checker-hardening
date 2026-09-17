package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.OperEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.containsOperator;
import static io.github.tlaplus.hardening.gen.engine.FormDecodingTestSupport.assertForm;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Function constructors and set maps that bind several names in one construct. */
class MultipleBinderTest {
    private static final FunctionType PAIR_FUNCTION = new FunctionType(
            new TupleType(List.of(PrimitiveType.BOOL, PrimitiveType.INT)), PrimitiveType.BOOL);

    @Test
    void aFunctionOfATupleMayBindOneNamePerComponent() {
        assertForm("[ arg0 \\in { TRUE, FALSE }, arg1 \\in { 1, 2, 3 } |-> arg0 ]",
                OtherExpressionKind.FUNCTION_DEFINITION, PAIR_FUNCTION, List.of(), 1, 1, 42);
        assertForm("[ arg0 \\in { <<TRUE, 1>>, <<FALSE, 2>>, <<TRUE, 3>> } |-> FALSE ]",
                OtherExpressionKind.FUNCTION_DEFINITION, PAIR_FUNCTION, List.of(), 1, 0, 42);
    }

    @Test
    void aFunctionOfAnythingElseSpendsNoMarkerOnTheChoice() {
        assertForm("[ arg0 \\in { TRUE, FALSE } |-> arg0 ]", OtherExpressionKind.FUNCTION_DEFINITION,
                new FunctionType(PrimitiveType.BOOL, PrimitiveType.BOOL), List.of(), 1, 42);
        assertForm("[ arg0 \\in { <<1>>, <<2>>, <<3>> } |-> FALSE ]", OtherExpressionKind.FUNCTION_DEFINITION,
                new FunctionType(new TupleType(List.of(PrimitiveType.INT)), PrimitiveType.BOOL),
                List.of(), 1, 42);
    }

    @Test
    void aSetMapBindsATerminatedListOfNames() {
        // Source types Boolean then integer, then the terminating marker.
        assertForm("{ mapped0: mapped0 \\in { TRUE, FALSE }, mapped1 \\in { 1, 2, 3 } }",
                SetExpressionKind.SET_MAP, new SetType(PrimitiveType.BOOL), List.of(), 1, 0, 1, 1, 0, 42);
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
}
