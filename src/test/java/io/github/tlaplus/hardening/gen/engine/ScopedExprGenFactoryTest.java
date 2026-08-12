package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ScopedExprGenFactoryTest {
    @Test
    void lexicalFormsCanSelectTheirIntroducedNames() {
        var scenarios = List.of(
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                GeneralExpressionKind.BOUNDED_CHOOSE,
                                0,
                                1,
                                0),
                        "bound0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                GeneralExpressionKind.UNBOUNDED_CHOOSE,
                                1,
                                0),
                        "chosen0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                BooleanExpressionKind.FORALL_BOUNDED,
                                0,
                                0,
                                1,
                                0),
                        "q0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                BooleanExpressionKind.TEMPORAL_EXISTS,
                                0,
                                1,
                                0),
                        "temporal0"),
                new Scenario(
                        new SetType(PrimitiveType.BOOL),
                        input(
                                new SetType(PrimitiveType.BOOL),
                                SetExpressionKind.SET_FILTER,
                                0,
                                1,
                                0),
                        "filtered0"),
                new Scenario(
                        new SetType(PrimitiveType.BOOL),
                        input(
                                new SetType(PrimitiveType.BOOL),
                                SetExpressionKind.SET_MAP,
                                0,
                                0,
                                1,
                                0),
                        "mapped0"),
                new Scenario(
                        new FunctionType(PrimitiveType.BOOL, PrimitiveType.BOOL),
                        input(
                                new FunctionType(PrimitiveType.BOOL, PrimitiveType.BOOL),
                                OtherExpressionKind.FUNCTION_DEFINITION,
                                0,
                                1,
                                0),
                        "arg0"),
                new Scenario(
                        new OperatorType(List.of(PrimitiveType.BOOL), PrimitiveType.BOOL),
                        input(
                                new OperatorType(
                                        List.of(PrimitiveType.BOOL), PrimitiveType.BOOL),
                                OtherExpressionKind.LAMBDA,
                                1,
                                0),
                        "parameter0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(PrimitiveType.BOOL, GeneralExpressionKind.LET, 0, 8, 0, 0),
                        "LocalOp0"));

        for (var scenario : scenarios) {
            var printed = generateAndPrint(scenario.type(), scenario.input());
            assertTrue(
                    occurrences(printed, scenario.name()) >= 2,
                    () -> scenario.name() + " was not selected in:\n" + printed);
        }
    }

    private String generateAndPrint(IrType type, byte[] input) {
        var draw = new Draw(input);
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        return print(context.builder().build(draw.draw(expressionFactory.mkGen(type, 8))));
    }

    private int occurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }

    private byte[] input(IrType type, ExpressionKind selectedKind, int... remainder) {
        var selectedIndex = 0;
        for (var kind : ExpressionKinds.all()) {
            if (!kind.isApplicable(type)) {
                continue;
            }
            if (kind == selectedKind) {
                var values = new int[remainder.length + 1];
                values[0] = selectedIndex;
                System.arraycopy(remainder, 0, values, 1, remainder.length);
                return bytes(values);
            }
            selectedIndex++;
        }
        throw new IllegalArgumentException(selectedKind + " is not applicable to " + type);
    }

    private byte[] bytes(int... values) {
        var result = new byte[values.length];
        for (var index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private String print(TlaEx expression) {
        var buffer = new StringWriter();
        var printWriter = new PrintWriter(buffer);
        new PrettyWriter(printWriter, new TextLayout(80, 2), new TlaDeclAnnotator())
                .write(expression);
        printWriter.flush();
        return buffer.toString();
    }

    private record Scenario(IrType type, byte[] input, String name) {}
}
