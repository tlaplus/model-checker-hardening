package io.github.tlaplus.hardening.gen.ir;

import static io.github.tlaplus.hardening.gen.ir.IrFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class IrSubstitutionTest {
    @Test
    void replacesFreeOccurrencesOnly() {
        var expression = B.plus(integer("x"), B.choose(integer("x"), set(), B.gt(integer("x"), one())));
        var result = IrSubstitution.substitute(expression, Map.of("x", B.integer(7)));
        assertEquals(B.plus(B.integer(7), B.choose(integer("x"), set(), B.gt(integer("x"), one()))), result);
    }

    @Test
    void refusesAReplacementABinderWouldCapture() {
        var expression = allAbove("q", integer("limit"));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> IrSubstitution.substitute(expression, Map.of("limit", integer("q"))));
        assertTrue(failure.getMessage().contains("capture"), failure.getMessage());
    }

    @Test
    void substitutesIntoTheDomainOfABinderThatShadowsTheName() {
        // In \A x \in x : x > 1, only the domain reads the outer x.
        var expression = B.forall(integer("x"), B.name("x", INT_SET), B.gt(integer("x"), one()));
        var result = IrSubstitution.substitute(expression, Map.of("x", set()));
        assertEquals(B.forall(integer("x"), set(), B.gt(integer("x"), one())), result);
    }

    /**
     * The rewriter substitutes a lambda for a higher-order parameter, which leaves the lambda applied.
     * A LET that applies its own definition is ordinary generated code and stays as it is.
     */
    @Test
    void betaReducesASubstitutedLambdaApplicationOnly() {
        var lambda = B.lambda("L", B.gt(integer("p"), one()), B.param("p", TlaTypes.INT));
        var parameter = B.name("P", TlaTypes.operator(TlaTypes.BOOL, TlaTypes.INT));
        var applied = IrSubstitution.substitute(B.operApply(parameter, integer("e")), Map.of("P", lambda));
        assertEquals(B.gt(integer("e"), one()), IrSubstitution.betaReduce(applied));
        var let = B.operApply(lambda, integer("e"));
        assertEquals(let, IrSubstitution.betaReduce(let));
        assertTrue(IrSubstitution.lambda(lambda).isPresent());
        assertTrue(IrSubstitution.lambda(integer("e")).isEmpty());
    }

    @Test
    void freeNamesExcludeBoundOnes() {
        assertEquals(Set.of("S", "limit"), IrNames.free(allAbove("q", integer("limit"))));
    }

    @Test
    void renamesEverySpellingIncludingBinders() {
        var renamed = IrNames.rename(allAbove("q", integer("limit")), name -> name + "_2");
        assertEquals(B.forall(integer("q_2"), B.name("S_2", INT_SET), B.gt(integer("q_2"), integer("limit_2"))), renamed);
    }
}
