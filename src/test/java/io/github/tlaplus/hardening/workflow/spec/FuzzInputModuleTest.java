package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TlaWriter$;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
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

        var operators = CollectionConverters.asJava(module.operDeclarations());
        assertEquals(3, operators.size());
        assertEquals("Init", operators.get(0).name());
        assertEquals("Next", operators.get(1).name());
        assertEquals("Inv", operators.get(2).name());

        var initExpression = equalityRightHandSide(operators.get(0));
        var invariantExpression = equalityRightHandSide(operators.get(2));
        assertNotEquals(initExpression.ID(), invariantExpression.ID());

        var source = PrettyWriter.writeAsString(
                module, TlaWriter$.MODULE$.STANDARD_MODULES());
        assertTrue(source.contains("EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants"));
        assertTrue(source.contains("VARIABLE exprValue"));
        assertTrue(source.contains("Next == UNCHANGED exprValue"));
        assertEquals(2, occurrences(source, "exprValue = FALSE"));
        assertFalse(source.contains("GeneratedExpression"));
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
