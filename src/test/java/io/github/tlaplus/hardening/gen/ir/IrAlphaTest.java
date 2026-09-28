package io.github.tlaplus.hardening.gen.ir;

import static io.github.tlaplus.hardening.gen.ir.IrFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import org.apalache_mc.tla.jir.ExpressionPair;
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

    @Test
    void aTuplePatternIsNotASingleName() {
        var domain = B.enumSet(B.tuple(one()));
        var plain = B.forall(B.name("x", TlaTypes.tuple(TlaTypes.INT)), domain, B.bool(true));
        var tuple = B.forall(B.tuple(integer("y")), domain, B.bool(true));
        assertEquivalent(false, plain, tuple);
    }

    @Test
    void renamedTupleComponentsMatchInOrder() {
        var domain = B.enumSet(B.tuple(one(), B.integer(2)));
        var left = B.forall(B.tuple(integer("a"), integer("b")), domain, B.gt(integer("a"), integer("b")));
        var right = B.forall(B.tuple(integer("x"), integer("y")), domain, B.gt(integer("x"), integer("y")));
        assertEquivalent(true, left, right);
        assertEquivalent(false, left,
                B.forall(B.tuple(integer("x"), integer("y")), domain, B.gt(integer("y"), integer("x"))));
    }

    @Test
    void unusedLetParametersMustHaveTheSameArity() {
        var parameterTypes = new TlaType1[] {TlaTypes.INT,
                TlaTypes.operator(TlaTypes.INT, TlaTypes.INT),
                TlaTypes.operator(TlaTypes.INT, TlaTypes.INT, TlaTypes.INT)};
        for (var left = 0; left < parameterTypes.length; left++) {
            for (var right = left; right < parameterTypes.length; right++) {
                assertEquivalent(left == right,
                        B.letIn(one(), B.decl("F", one(), B.param("x", parameterTypes[left]))),
                        B.letIn(one(), B.decl("G", one(), B.param("y", parameterTypes[right]))));
            }
        }
    }

    @Test
    void parameterTypeAnnotationsDoNotAffectEquivalence() {
        assertEquivalent(true,
                B.lambda("F", one(), B.param("x", TlaTypes.INT)),
                B.lambda("G", one(), B.param("y", TlaTypes.BOOL)));
        assertEquivalent(true,
                B.lambda("F", one(), B.param("P", TlaTypes.operator(TlaTypes.INT, TlaTypes.INT))),
                B.lambda("G", one(), B.param("Q", TlaTypes.operator(TlaTypes.BOOL, TlaTypes.BOOL))));
    }

    @Test
    void nestedShadowingUsesTheInnermostBinder() {
        var left = B.forall(integer("x"), set(), allAbove("x", one()));
        assertEquivalent(true, left, B.forall(integer("a"), set(), allAbove("b", one())));
        assertEquivalent(false, left,
                B.forall(integer("a"), set(), B.forall(integer("b"), set(), B.gt(integer("a"), one()))));
    }

    @Test
    void aNestedBinderDoesNotLeakIntoItsSibling() {
        var left = B.forall(integer("x"), set(), B.and(allAbove("x", one()), B.gt(integer("x"), one())));
        var right = B.forall(integer("a"), set(), B.and(allAbove("b", one()), B.gt(integer("a"), one())));
        assertEquivalent(true, left, right);
        assertEquivalent(false, left,
                B.forall(integer("a"), set(), B.and(allAbove("b", one()), B.gt(integer("b"), one()))));
    }

    @Test
    void aDomainSeesTheEnclosingBinderButNotItsOwnBinder() {
        var left = B.forall(integer("x"), set(),
                B.forall(integer("x"), B.enumSet(integer("x")), B.gt(integer("x"), one())));
        assertEquivalent(true, left, B.forall(integer("a"), set(),
                B.forall(integer("b"), B.enumSet(integer("a")), B.gt(integer("b"), one()))));
        assertEquivalent(false, left, B.forall(integer("a"), set(),
                B.forall(integer("b"), B.enumSet(integer("b")), B.gt(integer("b"), one()))));
    }

    @Test
    void multipleBinderDomainsStayOutsideAllTheirBinders() {
        var left = B.map(integer("x"), new ExpressionPair<>(integer("x"), set()),
                new ExpressionPair<>(integer("y"), B.enumSet(integer("x"))));
        var right = B.map(integer("a"), new ExpressionPair<>(integer("a"), set()),
                new ExpressionPair<>(integer("b"), B.enumSet(integer("x"))));
        assertEquivalent(true, left, right);
        assertEquivalent(false, left, B.map(integer("a"), new ExpressionPair<>(integer("a"), set()),
                new ExpressionPair<>(integer("b"), B.enumSet(integer("a")))));
    }

    private static void assertEquivalent(boolean expected, TlaEx left, TlaEx right) {
        assertEquals(expected, IrAlpha.equivalent(left, right));
        assertEquals(expected, IrAlpha.equivalent(right, left));
    }
}
