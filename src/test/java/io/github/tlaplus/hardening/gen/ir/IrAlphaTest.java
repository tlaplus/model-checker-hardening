package io.github.tlaplus.hardening.gen.ir;

import static io.github.tlaplus.hardening.gen.ir.IrFixtures.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class IrAlphaTest {
    @Test
    void renamingABinderPreservesEquivalence() {
        assertTrue(IrAlpha.equivalent(allAbove("q", one()), allAbove("r", one())));
    }

    @Test
    void aFreeNameMustMatchExactly() {
        assertTrue(IrAlpha.equivalent(allAbove("q", integer("x")), allAbove("r", integer("x"))));
        assertFalse(IrAlpha.equivalent(allAbove("q", integer("x")), allAbove("q", integer("y"))));
    }

    @Test
    void aBoundNameIsNotAFreeNameOfTheSameSpelling() {
        // \A q \in S : q > x  versus  \A x \in S : x > x
        assertFalse(IrAlpha.equivalent(allAbove("q", integer("x")), allAbove("x", integer("x"))));
    }

    @Test
    void lambdasAreEquivalentUpToTheirParameterNames() {
        var left = B.lambda("L1", B.gt(integer("p"), one()), B.param("p", TlaTypes.INT));
        var right = B.lambda("L2", B.gt(integer("r"), one()), B.param("r", TlaTypes.INT));
        assertTrue(IrAlpha.equivalent(left, right));
        assertFalse(IrAlpha.equivalent(left, B.lambda("L3", B.gt(one(), integer("r")), B.param("r", TlaTypes.INT))));
    }

    @Test
    void differentOperatorsOrArgumentsAreNotEquivalent() {
        assertFalse(IrAlpha.equivalent(B.plus(one(), one()), B.minus(one(), one())));
        assertFalse(IrAlpha.equivalent(B.plus(one(), one()), B.plus(one(), B.integer(2))));
    }
}
