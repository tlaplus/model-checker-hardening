package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class RuleMatcherTest {
    private static final TlaEx T = B.name("T", TlaTypes.set(TlaTypes.INT));

    @Test
    void aGenericRuleMatchesEveryNodeOfItsType() {
        var node = B.plus(integer("a"), B.integer(1));
        assertEquals(B.plus(B.plus(integer("a"), B.integer(1)), B.integer(0)), rewrite(plusZero(), node).orElseThrow());
        assertTrue(RuleMatcher.match(checked(plusZero()), bool("b")).isEmpty(), "PlusZero applies to integers only");
    }

    @Test
    void aFreshParameterTakesTheDrawnValue() {
        var match = RuleMatcher.match(checked(addSub()), integer("a")).orElseThrow();
        var result = RuleInstantiation.instantiate(match, Map.of("y", B.integer(5)), match.types(), name -> name);
        assertEquals(B.plus(integer("a"), B.minus(B.integer(5), B.integer(5))), result);
    }

    @Test
    void aPolymorphicRuleInstantiatesItsTypes() {
        var sets = B.name("SS", TlaTypes.set(TlaTypes.set(TlaTypes.INT)));
        for (var node : new TlaEx[] {T, sets}) {
            var result = rewrite(unionSelf(), node).orElseThrow();
            assertEquals(B.union(node, node), result);
            assertEquals(TlaTypes.typeOf(node), TlaTypes.typeOf(result));
        }
    }

    @Test
    void aRuleDoesNotMoveAnAssignmentOutOfAnAssigningPosition() {
        var assignment = B.primeEq(integer("x"), B.integer(1));
        assertTrue(RuleMatcher.match(checked(doubleNeg()), assignment).isEmpty(), "~~(x' = 1) assigns nothing in TLC");
        assertEquals(B.not(B.not(B.gt(integer("x"), B.integer(1)))),
                rewrite(doubleNeg(), B.gt(integer("x"), B.integer(1))).orElseThrow());

        var guard = B.gt(integer("y"), B.integer(0));
        assertEquals(B.and(guard, assignment), rewrite(commuteAnd(), B.and(assignment, guard)).orElseThrow(),
                "one primed conjunct may move");
        var second = B.primeEq(integer("y"), B.integer(2));
        assertTrue(RuleMatcher.match(checked(commuteAnd()), B.and(assignment, second)).isEmpty(),
                "two primed conjuncts must keep their order");
    }

    @Test
    void aRepeatedParameterRequiresEquivalentSubterms() {
        var subSelf = rule("SubSelf", B.eql(B.minus(integer("x"), integer("x")), B.integer(0)), B.param("x", TlaTypes.INT));
        assertTrue(rewrite(subSelf, B.minus(integer("a"), integer("a"))).isPresent());
        assertTrue(rewrite(subSelf, B.minus(integer("a"), integer("b"))).isEmpty());
    }

    @Test
    void aHigherOrderParameterAbstractsTheBoundVariable() {
        var node = B.forall(integer("q"), T, B.gt(integer("q"), integer("z")));
        var result = rewrite(forallNotExists(), node).orElseThrow();
        assertEquals(B.not(B.exists(integer("e_1"), T, B.not(B.gt(integer("e_1"), integer("z"))))), result);
    }

    @Test
    void aFirstOrderParameterCannotReadAVariableThePatternBinds() {
        var e = B.name("e", ELEMENT);
        var existsConstant = rule("ExistsConstant",
                B.eql(B.exists(e, elements("S"), bool("c")), B.and(B.neql(elements("S"), B.emptySet(ELEMENT)), bool("c"))),
                B.param("S", TlaTypes.set(ELEMENT)), B.param("c", TlaTypes.BOOL));
        assertTrue(rewrite(existsConstant, B.exists(integer("q"), T, B.gt(integer("z"), B.integer(0)))).isPresent());
        assertTrue(rewrite(existsConstant, B.exists(integer("q"), T, B.gt(integer("q"), B.integer(0)))).isEmpty());
    }

    /** Matches the rule and instantiates it with numbered binder names and no fresh parameter. */
    private static Optional<TlaEx> rewrite(TlaOperDecl definition, TlaEx node) {
        var counter = new AtomicInteger();
        return RuleMatcher.match(checked(definition), node).map(match -> RuleInstantiation.instantiate(
                match, Map.of(), match.types(), name -> name + "_" + counter.incrementAndGet()));
    }

    private static RewriteRule checked(TlaOperDecl definition) {
        return library(definition).rules().getFirst();
    }
}
