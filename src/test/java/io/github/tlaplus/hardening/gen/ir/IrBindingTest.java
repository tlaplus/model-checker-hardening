package io.github.tlaplus.hardening.gen.ir;

import static io.github.tlaplus.hardening.gen.ir.IrFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import java.util.List;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.TlaOperators;
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

    private static List<String> names(OperEx application) {
        return IrBinding.boundNames(application).stream().map(NameEx::name).toList();
    }
}
