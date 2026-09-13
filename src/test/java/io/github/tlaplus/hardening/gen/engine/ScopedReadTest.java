package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static io.github.tlaplus.hardening.gen.engine.FormDecodingTestSupport.assertForm;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Variant eliminators and function reads draw the type of the value they read from the visible
 * bindings or fresh, like the applicative forms. Operands are drawn at depth zero, so they are
 * byte-free terminals that name a visible binding of their type when one exists.
 */
class ScopedReadTest {
    private static final VariantType VARIANT = new VariantType(List.of(
            new Field("A", PrimitiveType.BOOL),
            new Field("B", PrimitiveType.INT),
            new Field("C", PrimitiveType.INT)));
    private static final VariantType OTHER_VARIANT = new VariantType(List.of(
            new Field("D", PrimitiveType.STRING)));
    private static final FunctionType FUNCTION = new FunctionType(PrimitiveType.INT, PrimitiveType.BOOL);

    @Test
    void aVariantReadCanInspectAVisibleNameAtAnyTagOfThePayloadType() {
        var v = List.of(ScopedName.stateVariable("v", VARIANT));
        assertForm("VariantGetUnsafe(\"B\", v)", GeneralExpressionKind.VARIANT_GET_UNSAFE,
                PrimitiveType.INT, v, 1, 1, 0, 42);
        assertForm("VariantGetUnsafe(\"C\", v)", GeneralExpressionKind.VARIANT_GET_UNSAFE,
                PrimitiveType.INT, v, 1, 1, 1, 42);
        assertForm("VariantGetOrElse(\"B\", v, 0)", GeneralExpressionKind.VARIANT_GET_OR_ELSE,
                PrimitiveType.INT, v, 1, 1, 0, 42);
        // A single matching tag spends no byte on the choice.
        assertForm("VariantGetUnsafe(\"A\", v)", GeneralExpressionKind.VARIANT_GET_UNSAFE,
                PrimitiveType.BOOL, v, 1, 1, 42);
    }

    @Test
    void aVariantFilterReadsAVisibleSetOfVariants() {
        assertForm("VariantFilter(\"C\", s)", SetExpressionKind.VARIANT_FILTER,
                new SetType(PrimitiveType.INT),
                List.of(ScopedName.binder("s", new SetType(VARIANT))), 1, 1, 1, 42);
    }

    @Test
    void aVariantTagChoosesAmongEveryReachableVariantType() {
        var bindings = List.of(
                ScopedName.binder("v", VARIANT), ScopedName.binder("w", OTHER_VARIANT));
        assertForm("VariantTag(v)", OtherExpressionKind.VARIANT_TAG, PrimitiveType.STRING,
                bindings, 1, 1, 0, 42);
        assertForm("VariantTag(w)", OtherExpressionKind.VARIANT_TAG, PrimitiveType.STRING,
                bindings, 1, 1, 1, 42);
    }

    @Test
    void anEvenMarkerOrAnEmptyScopeDrawsAFreshVariantWithDrawnAlternatives() {
        assertForm("VariantGetUnsafe(\"Tag0\", (Variant(\"Tag0\", 0)))",
                GeneralExpressionKind.VARIANT_GET_UNSAFE, PrimitiveType.INT,
                List.of(ScopedName.stateVariable("v", VARIANT)), 1, 0, 0, 42);
        // One more alternative of the first primitive type (Boolean), the payload placed second.
        assertForm("VariantGetUnsafe(\"Tag1\", (Variant(\"Tag0\", FALSE)))",
                GeneralExpressionKind.VARIANT_GET_UNSAFE, PrimitiveType.INT,
                List.of(), 1, 0, 1, 0, 0, 1, 42);
    }

    @Test
    void aFunctionReadCanApplyAndTakeTheDomainOfAVisibleName() {
        var f = List.of(ScopedName.stateVariable("f", FUNCTION));
        assertForm("f[0]", GeneralExpressionKind.FUNCTION_APPLICATION, PrimitiveType.BOOL, f, 1, 1, 42);
        assertForm("DOMAIN f", SetExpressionKind.DOMAIN, new SetType(PrimitiveType.INT), f, 1, 1, 42);
    }

    @Test
    void anEvenMarkerDrawsTheFunctionTypeAroundTheRequestedComponent() {
        // The drawn argument type is the first value type (Boolean), so the visible `f` does not fit.
        assertForm("[ terminalArg0 \\in {} |-> FALSE ][FALSE]", GeneralExpressionKind.FUNCTION_APPLICATION,
                PrimitiveType.BOOL, List.of(ScopedName.stateVariable("f", FUNCTION)), 1, 0, 0, 42);
        assertForm("DOMAIN ([ terminalArg0 \\in {} |-> FALSE ])", SetExpressionKind.DOMAIN,
                new SetType(PrimitiveType.INT), List.of(), 1, 1, 0, 42);
    }

    @Test
    void anExhaustedCursorStillBuildsEveryRead() {
        var requests = Map.<ExpressionKind, IrType>of(
                GeneralExpressionKind.VARIANT_GET_UNSAFE, PrimitiveType.INT,
                GeneralExpressionKind.VARIANT_GET_OR_ELSE, PrimitiveType.INT,
                SetExpressionKind.VARIANT_FILTER, new SetType(PrimitiveType.INT),
                OtherExpressionKind.VARIANT_TAG, PrimitiveType.STRING,
                GeneralExpressionKind.FUNCTION_APPLICATION, PrimitiveType.BOOL,
                SetExpressionKind.DOMAIN, new SetType(PrimitiveType.INT));
        requests.forEach((kind, type) -> {
            var run = new GenerationContext(IrGenerationConfig.defaults());
            var expressions = new IrExprGenFactory(run, new IrTypeGenFactory(run));
            assertFalse(print(new Draw(new byte[0]).draw(expressions.mkGen(kind, type, 1))).isEmpty(),
                    kind.toString());
        });
    }
}
