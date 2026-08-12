package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScopedExprGenFactoryTest {
    @Test
    void lexicalFormsCanSelectTheirIntroducedNames() {
        var scopedBooleanName = applicableIndex(
                PrimitiveType.BOOL,
                GeneralExpressionKind.NAME,
                new ScopedName("bound", PrimitiveType.BOOL));
        var localOperatorType = new OperatorType(List.of(), PrimitiveType.BOOL);
        var scopedOperatorApplication = applicableIndex(
                PrimitiveType.BOOL,
                GeneralExpressionKind.OPERATOR_APPLICATION,
                new ScopedName("LocalOp", localOperatorType));
        var scenarios = List.of(
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                GeneralExpressionKind.BOUNDED_CHOOSE,
                                0,
                                scopedBooleanName,
                                0),
                        "bound0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                GeneralExpressionKind.UNBOUNDED_CHOOSE,
                                scopedBooleanName,
                                0),
                        "chosen0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                BooleanExpressionKind.FORALL_BOUNDED,
                                0,
                                0,
                                scopedBooleanName,
                                0),
                        "q0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                BooleanExpressionKind.TEMPORAL_EXISTS,
                                0,
                                scopedBooleanName,
                                0),
                        "temporal0"),
                new Scenario(
                        new SetType(PrimitiveType.BOOL),
                        input(
                                new SetType(PrimitiveType.BOOL),
                                SetExpressionKind.SET_FILTER,
                                0,
                                scopedBooleanName,
                                0),
                        "filtered0"),
                new Scenario(
                        new SetType(PrimitiveType.BOOL),
                        input(
                                new SetType(PrimitiveType.BOOL),
                                SetExpressionKind.SET_MAP,
                                0,
                                0,
                                scopedBooleanName,
                                0),
                        "mapped0"),
                new Scenario(
                        new FunctionType(PrimitiveType.BOOL, PrimitiveType.BOOL),
                        input(
                                new FunctionType(PrimitiveType.BOOL, PrimitiveType.BOOL),
                                OtherExpressionKind.FUNCTION_DEFINITION,
                                0,
                                scopedBooleanName,
                                0),
                        "arg0"),
                new Scenario(
                        new OperatorType(List.of(PrimitiveType.BOOL), PrimitiveType.BOOL),
                        input(
                                new OperatorType(
                                        List.of(PrimitiveType.BOOL), PrimitiveType.BOOL),
                                OtherExpressionKind.LAMBDA,
                                scopedBooleanName,
                                0),
                        "parameter0"),
                new Scenario(
                        PrimitiveType.BOOL,
                        input(
                                PrimitiveType.BOOL,
                                GeneralExpressionKind.LET,
                                0,
                                scopedOperatorApplication),
                        "LocalOp0"));

        for (var scenario : scenarios) {
            var printed = generateAndPrint(scenario.type(), scenario.input());
            assertTrue(
                    occurrences(printed, scenario.name()) >= 2,
                    () -> scenario.name() + " was not selected in:\n" + printed);
        }
    }

    @Test
    void nameDependentFormsAreAvailableOnlyWithCompatibleBindings() {
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);

        assertFalse(expressionFactory.isApplicable(
                GeneralExpressionKind.NAME, PrimitiveType.BOOL));
        assertFalse(expressionFactory.isApplicable(
                GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.BOOL));

        var value = new ScopedName("value", PrimitiveType.BOOL);
        new Draw(new byte[0]).draw(context.withBinding(value, ignored -> {
            assertTrue(expressionFactory.isApplicable(
                    GeneralExpressionKind.NAME, PrimitiveType.BOOL));
            assertFalse(expressionFactory.isApplicable(
                    GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.BOOL));
            return null;
        }));

        var operatorType = new OperatorType(
                List.of(PrimitiveType.INT), PrimitiveType.BOOL);
        var operator = new ScopedName("Predicate", operatorType);
        new Draw(new byte[0]).draw(context.withBinding(operator, ignored -> {
            assertTrue(expressionFactory.isApplicable(
                    GeneralExpressionKind.NAME, operatorType));
            assertTrue(expressionFactory.isApplicable(
                    GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.BOOL));
            assertFalse(expressionFactory.isApplicable(
                    GeneralExpressionKind.OPERATOR_APPLICATION, PrimitiveType.INT));
            return null;
        }));
    }

    @Test
    void primeEqualRejectsOrdinaryBindingsButUsesStateVariables() {
        var missingContext = new GenerationContext(IrGenerationConfig.defaults());
        var missingTypes = new IrTypeGenFactory(missingContext);
        var missingExpressions = new IrExprGenFactory(missingContext, missingTypes);
        var missingFactory = new BooleanExprGenFactory(
                missingContext, missingTypes, missingExpressions);

        assertTrue(missingExpressions.isApplicable(
                BooleanExpressionKind.PRIME_EQUAL, PrimitiveType.BOOL));
        assertThrows(
                InputRejectedException.class,
                () -> new Draw(new byte[0]).draw(
                        missingFactory.mkGen(BooleanExpressionKind.PRIME_EQUAL, 1)));

        var ordinary = new ScopedName("ordinary", PrimitiveType.INT);
        assertThrows(
                InputRejectedException.class,
                () -> new Draw(new byte[0]).draw(missingContext.withBinding(
                        ordinary,
                        missingFactory.mkGen(BooleanExpressionKind.PRIME_EQUAL, 1))));

        var stateContext = new GenerationContext(IrGenerationConfig.defaults());
        var stateTypes = new IrTypeGenFactory(stateContext);
        var stateExpressions = new IrExprGenFactory(stateContext, stateTypes);
        var stateFactory = new BooleanExprGenFactory(
                stateContext, stateTypes, stateExpressions);
        var state = ScopedName.stateVariable("state", PrimitiveType.INT);
        var expression = new Draw(new byte[0]).draw(stateContext.withBinding(
                state, stateFactory.mkGen(BooleanExpressionKind.PRIME_EQUAL, 1)));

        var printed = print(stateContext.builder().build(expression));
        assertTrue(printed.contains("state'"), printed);
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
        var values = new int[remainder.length + 1];
        values[0] = applicableIndex(type, selectedKind);
        System.arraycopy(remainder, 0, values, 1, remainder.length);
        return bytes(values);
    }

    private int applicableIndex(
            IrType type, ExpressionKind selectedKind, ScopedName... bindings) {
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        return new Draw(new byte[0]).draw(context.withBindings(
                List.of(bindings),
                ignored -> {
                    var selectedIndex = 0;
                    for (var kind : ExpressionKinds.all()) {
                        if (!expressionFactory.isApplicable(kind, type)) {
                            continue;
                        }
                        if (kind == selectedKind) {
                            return selectedIndex;
                        }
                        selectedIndex++;
                    }
                    throw new IllegalArgumentException(
                            selectedKind + " is not applicable to " + type);
                }));
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
