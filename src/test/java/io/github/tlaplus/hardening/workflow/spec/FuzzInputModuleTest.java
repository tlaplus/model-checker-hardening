package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import org.junit.jupiter.api.Test;
import scala.jdk.javaapi.CollectionConverters;

class FuzzInputModuleTest {
    @Test
    void constructsOneStateVariableAndCopiesTheExpressionIntoInitAndInv() {
        var expression = IrGenerators.expressions().generate(new byte[0]);

        var module = FuzzInputModule.create(expression);

        assertEquals("FuzzInput", module.name());
        assertEquals(1, module.varDeclarations().size());
        assertEquals("exprValue", module.varDeclarations().head().name());

        // The definition names are the contract with the fixed tool invocations, and Bound is
        // always defined so that one TLC configuration serves every input kind.
        var operators = CollectionConverters.asJava(module.operDeclarations());
        assertEntryPointOrder(operators.subList(operators.size() - 4, operators.size()));

        var initExpression = equalityRightHandSide(operators.get(0));
        var invariantExpression = equalityRightHandSide(operators.get(2));
        assertNotEquals(initExpression.ID(), invariantExpression.ID());

        var source = PrettyWriter.writeAsString(
                module, TlaWriter$.MODULE$.STANDARD_MODULES());
        assertTrue(source.contains("EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants"));
        assertTrue(source.contains("VARIABLE exprValue"));
        assertTrue(source.contains("Next == UNCHANGED exprValue"));
        assertTrue(source.contains("Bound == TRUE"));
        assertEquals(2, occurrences(source, "exprValue = FALSE"));
        assertFalse(source.contains("GeneratedExpression"));
    }

    @Test
    void appendsTheSameEntryPointsToAGeneratedModule() {
        var spec = IrGenerators.specs(IrGenerationConfig.defaults()).generate(new byte[0]);

        var module = FuzzInputModule.create(spec);

        var operators = CollectionConverters.asJava(module.operDeclarations());
        assertEntryPointOrder(operators.subList(operators.size() - 4, operators.size()));
    }

    private void assertEntryPointOrder(
            java.util.List<TlaOperDecl> entryPoints) {
        assertEquals(4, entryPoints.size());
        assertEquals(FuzzInputModule.INIT, entryPoints.get(0).name());
        assertEquals(FuzzInputModule.NEXT, entryPoints.get(1).name());
        assertEquals(FuzzInputModule.INV, entryPoints.get(2).name());
        assertEquals(FuzzInputModule.BOUND, entryPoints.get(3).name());
    }

    private at.forsyte.apalache.tla.lir.TlaEx equalityRightHandSide(
            TlaOperDecl declaration) {
        var equality = (OperEx) declaration.body();
        return equality.args().apply(1);
    }

    private int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }
}
