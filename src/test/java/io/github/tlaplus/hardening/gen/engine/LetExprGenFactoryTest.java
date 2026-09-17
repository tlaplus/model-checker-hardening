package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static io.github.tlaplus.hardening.gen.engine.FormDecodingTestSupport.assertForm;
import static io.github.tlaplus.hardening.gen.engine.FormDecodingTestSupport.firstSlot;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/**
 * A LET declares a terminated list of local operators, each with drawn parameter types that may
 * include operator parameters. Byte values below name the decoder's choices in draw order: the
 * parameter-list markers and types, the result marker, the body's two selection bytes, and the
 * declaration-list marker.
 */
class LetExprGenFactoryTest {
    /** The value-type decoder's index of the integer type. */
    private static final int INT = 1;
    /** The parameter-type decoder's index of an operator type under the default configuration. */
    private static final int OPERATOR = 10;

    private static final OperatorType UNARY = new OperatorType(List.of(PrimitiveType.INT), PrimitiveType.INT);
    private static final OperatorType HIGHER_ORDER = new OperatorType(List.of(UNARY), PrimitiveType.INT);

    @Test
    void aDeclarationWithParametersIsAppliedInTheBody() {
        var apply = firstSlot(GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.INT,
                List.of(ScopedName.definition("LocalOp", UNARY)));
        assertForm("LET LocalOp1(parameter0) == parameter0 IN LocalOp1(1)",
                GeneralExpressionKind.LET, PrimitiveType.INT, List.of(), 2,
                1, INT, 0, 0, 0, 0, 0, 0, apply, 42);
    }

    @Test
    void aLaterDeclarationSeesTheEarlierOnes() {
        var nullary = new OperatorType(List.of(), PrimitiveType.INT);
        var apply = firstSlot(GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.INT,
                List.of(ScopedName.definition("LocalOp", nullary)));
        assertForm("LET LocalOp0 == 1 IN LET LocalOp1 == LocalOp0 IN LocalOp1",
                GeneralExpressionKind.LET, PrimitiveType.INT, List.of(), 2,
                0, 0, 0, 0, 1,
                0, 0, 0, apply, 0,
                0, apply, 1, 42);
    }

    @Test
    void anOperatorParameterIsAppliedAndReceivesALambda() {
        var applyParameter = firstSlot(GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.INT,
                List.of(ScopedName.definition("F", UNARY)));
        var applyDeclaration = firstSlot(GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.INT,
                List.of(ScopedName.definition("LocalOp", HIGHER_ORDER)));
        assertForm("LET LocalOp1(parameter0(_)) == parameter0(1) IN "
                        + "(LET Lambda3(parameter2) == parameter2 IN LocalOp1(Lambda3))",
                GeneralExpressionKind.LET, PrimitiveType.INT, List.of(), 2,
                1, OPERATOR, INT, 0, INT, 0, 0, 0, applyParameter, 0, 0, applyDeclaration, 42);
    }

    @Test
    void aVisibleOperatorCanBePassedByName() {
        var unary = ScopedName.definition("LocalOp", UNARY);
        var applyParameter = firstSlot(GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.INT,
                List.of(unary, ScopedName.definition("F", UNARY)));
        var applyDeclaration = firstSlot(GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.INT,
                List.of(unary, ScopedName.definition("G", HIGHER_ORDER)));
        var name = firstSlot(GeneralExpressionKind.NAME, UNARY, List.of(unary));
        assertForm("LET LocalOp1(parameter0) == parameter0 IN "
                        + "LET LocalOp3(parameter2(_)) == parameter2(1) IN LocalOp3(LocalOp1)",
                GeneralExpressionKind.LET, PrimitiveType.INT, List.of(), 3,
                1, INT, 0, 0, 0, 0, 1,
                1, OPERATOR, INT, 0, INT, 0, 0, 0, applyParameter, 1, 0, 0, 0,
                0, applyDeclaration, 1, 0, name, 42);
    }

    @Test
    void sampledLetsAreNeverRecursiveAndReachEveryDeclarationShape() {
        var config = IrGenerationConfig.defaults().withFormWeights(Map.of(
                GeneralExpressionKind.LET, IrGenerationConfig.MAXIMUM_FORM_WEIGHT,
                GeneralExpressionKind.OPERATOR_APPLICATION, IrGenerationConfig.MAXIMUM_FORM_WEIGHT));
        var random = new Random(0x1e7L);
        var shapes = new Shapes();
        for (var sample = 0; sample < 300; sample++) {
            var run = new GenerationContext(config);
            var expressions = new IrExprGenFactory(run, new IrTypeGenFactory(run));
            var input = new byte[64 + random.nextInt(256)];
            random.nextBytes(input);
            var expression = new Draw(input).draw(expressions.mkGen(GeneralExpressionKind.LET, PrimitiveType.INT, 6));
            TlaIrTestSupport.assertLabelParameters(expression);
            TlaExpressions.forEach(expression, node -> shapes.visit(node, expression));
        }
        assertTrue(shapes.parameterized, "no declaration with parameters was generated");
        assertTrue(shapes.higherOrder, "no operator parameter was generated");
        assertTrue(shapes.operatorArgument, "no operator was passed as an argument");
    }

    /** Records which declaration shapes a sample reached. */
    private static final class Shapes {
        private boolean parameterized;
        private boolean higherOrder;
        private boolean operatorArgument;

        void visit(TlaEx node, TlaEx sample) {
            if (node instanceof LetInEx letIn) {
                for (var declaration : TlaExpressions.localDeclarations(letIn)) {
                    var own = declaration.name();
                    TlaExpressions.forEach(declaration.body(), inner -> assertFalse(
                            inner instanceof NameEx name && name.name().equals(own),
                            () -> own + " refers to itself in " + print(sample)));
                    var parameters = TlaDeclarations.parameters(declaration);
                    parameterized |= !parameters.isEmpty();
                    higherOrder |= parameters.stream().anyMatch(parameter -> parameter.type() instanceof OperT1);
                }
            }
            if (node instanceof OperEx operator && operator.oper() == TlaOperators.OPER_APP) {
                operatorArgument |= TlaExpressions.arguments(operator).stream().skip(1)
                        .anyMatch(argument -> TlaTypes.typeOf(argument) instanceof OperT1);
            }
        }
    }
}
