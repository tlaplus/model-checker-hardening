package io.github.tlaplus.hardening.gen.ir;

import static io.github.tlaplus.hardening.gen.ir.IrFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.List;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class IrBindingTest {
    @Test
    void aBoundedQuantifierBindsItsFirstArgumentInItsBody() {
        var binding = IrBinding.of(TlaOperators.FORALL3);
        assertEquals(IrBinding.SINGLE_BOUNDED, binding);
        assertTrue(binding.introduces(0));
        assertFalse(binding.scopes(1, 3));
        assertTrue(binding.scopes(2, 3));
        assertEquals(List.of("q"), names((OperEx) allAbove("q", one())));
    }

    @Test
    void aMultipleBinderBindsEveryOddArgumentInItsBodyOnly() {
        var map = (OperEx) B.map(B.plus(integer("a"), integer("b")),
                new ExpressionPair<>(integer("a"), set()), new ExpressionPair<>(integer("b"), set()));
        var binding = IrBinding.of(map.oper());
        assertEquals(IrBinding.MULTIPLE, binding);
        assertTrue(binding.scopes(0, 5));
        assertFalse(binding.scopes(2, 5));
        assertEquals(List.of("a", "b"), names(map));
    }

    @Test
    void anOrdinaryOperatorBindsNothing() {
        assertEquals(IrBinding.NONE, IrBinding.of(TlaOperators.PLUS));
        assertEquals(List.of(), names((OperEx) B.plus(one(), one())));
    }

    @Test
    void binderShapesIgnoreNamesAndTypesButPreserveTupleStructure() {
        assertShape(true, integer("x"), B.name("y", TlaTypes.BOOL));
        assertShape(true, B.tuple(integer("x"), B.tuple(integer("y"))),
                B.tuple(integer("a"), B.tuple(integer("b"))));
        assertShape(false, integer("x"), B.tuple(integer("y")));
        assertShape(false, B.tuple(integer("x")), B.tuple(integer("a"), integer("b")));
        assertShape(false, B.tuple(B.tuple(integer("x")), integer("y")),
                B.tuple(integer("a"), B.tuple(integer("b"))));
    }

    @Test
    void unsupportedBinderShapesDoNotMatchEvenThemselves() {
        for (var binder : new TlaEx[] {one(), B.plus(integer("x"), integer("y")), B.tuple(one())}) {
            assertShape(false, binder, binder);
            assertShape(false, binder, integer("x"));
        }
    }

    private static void assertShape(boolean expected, TlaEx left, TlaEx right) {
        assertEquals(expected, IrBinding.sameShape(left, right));
        assertEquals(expected, IrBinding.sameShape(right, left));
    }

    private static List<String> names(OperEx application) {
        return IrBinding.boundNames(application).stream().map(NameEx::name).toList();
    }
}
