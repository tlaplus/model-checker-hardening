package io.github.tlaplus.hardening.workflow.spec;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.actionOperators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.util.List;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.junit.jupiter.api.Test;

class FuzzInputModuleTest {
    /** The entry points and the fairness they share, which every assembled module ends with. */
    private static final int ENTRY_POINTS = 7;

    @Test
    void constructsOneStateVariableAndCopiesTheExpressionIntoInitAndInv() {
        var expression = IrGenerators.expressions().generate(new byte[0]);

        var module = FuzzInputModule.create(expression);

        assertEquals("FuzzInput", module.name());
        var variables = TlaModules.declarations(module).stream()
                .filter(TlaVarDecl.class::isInstance)
                .map(TlaVarDecl.class::cast)
                .toList();
        assertEquals(1, variables.size());
        assertEquals("exprValue", variables.getFirst().name());

        // The definition names are the contract with the fixed tool invocations, and every entry
        // point is defined so that one TLC configuration shape serves every input kind.
        var operators = operators(module);
        assertEntryPointOrder(operators.subList(operators.size() - ENTRY_POINTS, operators.size()));

        var initExpression = equalityRightHandSide(operators.get(0));
        var invariantExpression = equalityRightHandSide(operators.get(2));
        assertNotEquals(initExpression.ID(), invariantExpression.ID());

        var source = SpecText.render(module);
        assertTrue(source.contains("EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants"));
        assertTrue(source.contains("VARIABLE"));
        assertTrue(source.contains("exprValue"));
        assertTrue(source.contains("Next == UNCHANGED exprValue"));
        assertTrue(source.contains("Fairness == TRUE"));
        assertTrue(source.contains("Prop == TRUE"));
        assertTrue(source.contains("Liveness == Fairness => Prop"), source);
        assertTrue(source.contains("Spec == Init /\\ []([Next]_(<<exprValue>>)) /\\ Fairness"), source);
        assertEquals(2, occurrences(source, "exprValue = FALSE"));
        assertFalse(source.contains("GeneratedExpression"));
    }

    @Test
    void appendsTheSameEntryPointsToAGeneratedModule() {
        var spec = IrGenerators.specs(IrGenerationConfig.defaults()).generate(new byte[0]);

        var module = FuzzInputModule.create(spec);

        var operators = operators(module);
        assertEntryPointOrder(operators.subList(operators.size() - ENTRY_POINTS, operators.size()));
    }

    @Test
    void closesAGeneratedNextStateActionWithAStutteringDisjunctOverEveryVariable() {
        var spec = IrGenerators.specs(IrGenerationConfig.defaults()).generate(new byte[0]);

        var next = operators(FuzzInputModule.create(spec)).stream()
                .filter(operator -> operator.name().equals(FuzzInputModule.NEXT))
                .findFirst()
                .orElseThrow();

        assertEquals(
                "((step < 5 /\\ var0' = FALSE /\\ step' = step + 1)) \\/ UNCHANGED (<<var0, step>>)",
                io.github.tlaplus.hardening.gen.TlaIrTestSupport.print(next.body()));
    }

    @Test
    void placesActionOperatorsAfterAuxiliaryOperatorsAndBeforeTheEntryPoints() {
        var random = new java.util.Random(0xF1F0L);
        for (var sample = 0; sample < 2000; sample++) {
            var input = new byte[400 + random.nextInt(800)];
            random.nextBytes(input);
            final io.github.tlaplus.hardening.gen.GeneratedSpec spec;
            try {
                spec = IrGenerators.specs(IrGenerationConfig.defaults()).generate(input);
            } catch (io.github.tlaplus.hardening.gen.InputRejectedException rejected) {
                continue;
            }
            if (actionOperators(spec).isEmpty()) {
                continue;
            }
            var names = operators(FuzzInputModule.create(spec)).stream()
                    .map(TlaOperDecl::name)
                    .toList();
            var lastAuxiliary = lastIndexWithPrefix(names, "Op");
            var firstAction = names.indexOf(actionOperators(spec).get(0).declaration().name());
            var initIndex = names.indexOf(FuzzInputModule.INIT);
            assertTrue(firstAction > lastAuxiliary, "action operator precedes an auxiliary one");
            assertTrue(firstAction < initIndex, "action operator follows Init");
            return;
        }
        throw new AssertionError("no generated module declared an action operator");
    }

    private int lastIndexWithPrefix(java.util.List<String> names, String prefix) {
        var index = -1;
        for (var position = 0; position < names.size(); position++) {
            if (names.get(position).startsWith(prefix)) {
                index = position;
            }
        }
        return index;
    }

    private void assertEntryPointOrder(
            java.util.List<TlaOperDecl> entryPoints) {
        assertEquals(
                List.of(FuzzInputModule.INIT, FuzzInputModule.NEXT, FuzzInputModule.INV,
                        FuzzInputModule.FAIRNESS, FuzzInputModule.SPEC, FuzzInputModule.PROP,
                        FuzzInputModule.LIVENESS),
                entryPoints.stream().map(TlaOperDecl::name).toList());
    }

    private at.forsyte.apalache.tla.lir.TlaEx equalityRightHandSide(
            TlaOperDecl declaration) {
        var equality = (OperEx) declaration.body();
        return TlaExpressions.arguments(equality).get(1);
    }

    private List<TlaOperDecl> operators(at.forsyte.apalache.tla.lir.TlaModule module) {
        return TlaModules.declarations(module).stream()
                .filter(TlaOperDecl.class::isInstance)
                .map(TlaOperDecl.class::cast)
                .toList();
    }

    private int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }
}
