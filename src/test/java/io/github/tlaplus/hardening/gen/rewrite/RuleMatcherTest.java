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

    /** TLC assigns x' = x only for a variable x, so UNCHANGED <<a, b>> keeps its form. */
    @Test
    void aPrimedParameterMustBindAName() {
        var single = B.unchanged(integer("a"));
        assertEquals(B.primeEq(integer("a"), integer("a")), rewrite(unchangedPrime(), single).orElseThrow());
        var tuple = B.unchanged(B.tuple(integer("a"), integer("b")));
        assertTrue(RuleMatcher.match(checked(unchangedPrime()), tuple).isEmpty());
    }

    @Test
    void aRepeatedParameterRequiresEquivalentSubterms() {
        var subSelf = rule("SubSelf", B.eql(B.minus(integer("x"), integer("x")), B.integer(0)), B.param("x", TlaTypes.INT));
        assertTrue(rewrite(subSelf, B.minus(integer("a"), integer("a"))).isPresent());
        assertTrue(rewrite(subSelf, B.minus(integer("a"), integer("b"))).isEmpty());
    }

    @Test
    void repeatedBooleanParametersPreserveBinderStructureAndParameterArity() {
        var andSelf = checked(rule("AndSelf", B.eql(B.and(bool("P"), bool("P")), bool("P")),
                B.param("P", TlaTypes.BOOL)));
        var left = B.forall(integer("x"), T, B.gt(integer("x"), B.integer(0)));
        var renamed = B.forall(integer("y"), T, B.gt(integer("y"), B.integer(0)));
        assertTrue(RuleMatcher.match(andSelf, B.and(left, renamed)).isPresent());

        var domain = B.enumSet(B.tuple(B.integer(1)));
        var plain = B.forall(B.name("x", TlaTypes.tuple(TlaTypes.INT)), domain, B.bool(true));
        var tuple = B.forall(B.tuple(integer("y")), domain, B.bool(true));
        assertTrue(RuleMatcher.match(andSelf, B.and(plain, tuple)).isEmpty());
        assertTrue(RuleMatcher.match(andSelf, B.and(tuple, plain)).isEmpty());

        var valueParameter = B.letIn(B.bool(true), B.decl("F", B.integer(1), B.param("x", TlaTypes.INT)));
        var operatorParameter = B.letIn(B.bool(true),
                B.decl("G", B.integer(1), B.param("Q", TlaTypes.operator(TlaTypes.INT, TlaTypes.INT))));
        assertTrue(RuleMatcher.match(andSelf, B.and(valueParameter, operatorParameter)).isEmpty());
        assertTrue(RuleMatcher.match(andSelf, B.and(operatorParameter, valueParameter)).isEmpty());
    }

    @Test
    void directBinderMatchingPreservesTupleStructure() {
        // Empty domains have no children whose matching could reject incompatible binder types.
        var plainPattern = B.forall(B.name("e", ELEMENT), B.emptySet(ELEMENT), B.bool(true));
        var plainRule = checked(rule("PlainEmpty", B.eql(plainPattern, B.bool(true))));
        var tupleNode = B.forall(B.tuple(integer("q")), B.emptySet(TlaTypes.tuple(TlaTypes.INT)), B.bool(true));
        assertTrue(RuleMatcher.match(plainRule, tupleNode).isEmpty());

        var tuplePattern = B.forall(B.tuple(B.name("e", ELEMENT)),
                B.emptySet(TlaTypes.tuple(ELEMENT)), B.bool(true));
        var tupleRule = checked(rule("TupleEmpty", B.eql(tuplePattern, B.bool(true))));
        assertTrue(RuleMatcher.match(tupleRule, tupleNode).isPresent());
        var plainNode = B.forall(integer("q"), B.emptySet(TlaTypes.INT), B.bool(true));
        assertTrue(RuleMatcher.match(tupleRule, plainNode).isEmpty());
        assertTrue(RuleMatcher.match(plainRule, plainNode).isPresent());
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

    /**
     * SANY requires a label to declare the binders around it, so a label of a binding may not move
     * under a binder the replacement introduces; it may stay where no new binder reaches it.
     */
    @Test
    void aLabelledBindingDoesNotMoveUnderABinderOfTheReplacement() {
        var labelledBody = B.forall(integer("q"), T, B.label(B.gt(integer("q"), integer("z")), "lab", "q"));
        assertTrue(rewrite(forallNotExists(), labelledBody).isEmpty(), "the label would declare q, not the new binder");
        var labelledDomain = B.forall(integer("q"), B.label(T, "lab"), B.gt(integer("q"), integer("z")));
        assertTrue(rewrite(forallNotExists(), labelledDomain).isPresent(), "the domain is not under the binder");
    }

    /** SANY rejects every label inside an EXCEPT replacement. */
    @Test
    void aLabelledBindingDoesNotMoveIntoAnExceptReplacement() {
        var functions = TlaTypes.function(TlaTypes.INT, TlaTypes.INT);
        var f = B.name("f", functions);
        var exceptSelf = rule("ExceptSelf",
                B.eql(f, B.except(B.name("f", functions), integer("x"), B.funApply(B.name("f", functions), integer("x")))),
                B.param("f", functions), B.param("x", TlaTypes.INT));
        var function = B.name("g", functions);
        assertTrue(RuleMatcher.match(checked(exceptSelf), function).isPresent());
        assertTrue(RuleMatcher.match(checked(exceptSelf), B.label(function, "lab")).isEmpty(),
                "the copy of g in the replacement would carry its label");
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
