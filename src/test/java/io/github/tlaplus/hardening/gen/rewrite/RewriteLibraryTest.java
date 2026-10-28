package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.gen.rewrite.RuleParameter.Kind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class RewriteLibraryTest {
    @Test
    void classifiesTheParametersOfTheSeedRules() {
        var library = library(plusZero(), addSub(), doubleNeg(), unionSelf(), unchangedPrime(), forallNotExists());
        assertEquals(List.of("PlusZero", "AddSub", "DoubleNeg", "UnionSelf", "UnchangedPrime", "ForallNotExists"),
                library.rules().stream().map(RewriteRule::name).toList());
        assertEquals(Map.of("x", Kind.MATCHED, "y", Kind.FRESH), kinds(library, "AddSub"));
        assertEquals(Map.of("S", Kind.MATCHED, "P", Kind.HIGHER_ORDER), kinds(library, "ForallNotExists"));
        assertEquals(1, find(library, "ForallNotExists").parameter("P").orElseThrow().arity());
        assertTrue(library.rules().stream().allMatch(rule -> rule.weight() == RewriteLibrary.DEFAULT_WEIGHT));
    }

    @Test
    void indexesRulesByTheHeadOfTheirPatternAndKeepsGenericRulesEverywhere() {
        var library = library(plusZero(), unionSelf(), forallNotExists());
        // PlusZero and UnionSelf have a bare parameter as pattern, so they are candidates anywhere.
        assertEquals(List.of("PlusZero", "UnionSelf", "ForallNotExists"),
                ruleNames(library.candidates(B.forall(integer("q"), B.name("T", TlaTypes.set(TlaTypes.INT)), B.bool(true)))));
        assertEquals(List.of("PlusZero", "UnionSelf"), ruleNames(library.candidates(B.plus(B.integer(1), B.integer(2)))));
    }

    @Test
    void precomputesWhatARuleDoesToAssignments() {
        var library = library(doubleNeg(), commuteAnd());
        var negation = find(library, "DoubleNeg").assignments();
        assertTrue(negation.admits(Set.of()));
        assertFalse(negation.admits(Set.of("P")), "~~(x' = 1) is not an assignment");
        var commutation = find(library, "CommuteAnd").assignments();
        assertTrue(commutation.admits(Set.of("P")));
        assertFalse(commutation.admits(Set.of("P", "Q")), "reordering primed conjuncts moves an assignment");
    }

    @Test
    void appliesWeightsAndRejectsUnknownOrOutOfRangeOnes() {
        var rules = new TlaOperDecl[] {plusZero(), doubleNeg()};
        var weighted = RewriteLibrary.fromModule(module(rules), names(rules), Map.of("DoubleNeg", 5, "PlusZero", 0));
        assertEquals(List.of(0, 5), weighted.rules().stream().map(RewriteRule::weight).toList());
        assertRejected("weight names no rewrite rule: Missing",
                () -> RewriteLibrary.fromModule(module(rules), names(rules), Map.of("Missing", 1)));
        assertRejected("must be in the range 0..64",
                () -> RewriteLibrary.fromModule(module(rules), names(rules), Map.of("PlusZero", 65)));
        assertRejected("at least one rewrite rule needs a positive weight",
                () -> RewriteLibrary.fromModule(module(rules), names(rules), Map.of("PlusZero", 0, "DoubleNeg", 0)));
    }

    @Test
    void helpersAreNotRulesButReplacementsMayApplyThem() {
        var helper = B.decl("Zero", B.integer(0));
        var rule = rule("PlusHelper", B.eql(integer("x"), B.plus(integer("x"),
                B.operApply(B.name("Zero", TlaTypes.operator(TlaTypes.INT))))), B.param("x", TlaTypes.INT));
        var library = RewriteLibrary.fromModule(module(helper, rule), Set.of("PlusHelper"), Map.of());
        assertEquals(List.of("PlusHelper"), ruleNames(library.rules()));
    }

    @Test
    void rejectsDefinitionsThatBreakTheRuleContract() {
        assertRejected("the body must be A = B or A <=> B",
                () -> library(rule("NotARule", B.plus(integer("x"), B.integer(0)), B.param("x", TlaTypes.INT))));
        assertRejected("the replacement reads z, which is neither a parameter nor a helper",
                () -> library(rule("Unknown", B.eql(integer("x"), B.plus(integer("x"), integer("z"))),
                        B.param("x", TlaTypes.INT))));
        assertRejected("the parameter z is unused",
                () -> library(rule("Unused", B.eql(integer("x"), B.plus(integer("x"), B.integer(0))),
                        B.param("x", TlaTypes.INT), B.param("z", TlaTypes.INT))));
        assertRejected("the replacement applies PRIME, but the pattern is not an action",
                () -> library(rule("Raise", B.eql(bool("P"), B.and(bool("P"), B.primeEq(integer("x"), integer("x")))),
                        B.param("P", TlaTypes.BOOL), B.param("x", TlaTypes.INT))));
        assertRejected("temporal rules are not supported yet",
                () -> library(rule("Always", B.equiv(B.always(bool("F")), B.always(B.always(bool("F")))),
                        B.param("F", TlaTypes.BOOL))));
    }

    @Test
    void rejectsAHigherOrderParameterOutsideMillersFragment() {
        var p = B.name("P", PREDICATE);
        var applied = B.operApply(p, B.name("x", ELEMENT));
        assertRejected("P must be applied to distinct variables the pattern binds",
                () -> library(rule("Free", B.eql(applied, B.not(B.not(B.operApply(B.name("P", PREDICATE),
                                B.name("x", ELEMENT))))),
                        B.param("x", ELEMENT), B.param("P", PREDICATE))));
    }

    /** A lambda left unapplied would reach the output, so a replacement passes LAMBDA x : P(x) instead. */
    @Test
    void rejectsAnOperatorParameterThatTheReplacementDoesNotApply() {
        var keepType = TlaTypes.operator(TlaTypes.set(ELEMENT), TlaTypes.set(ELEMENT), PREDICATE);
        var keep = B.decl("Keep", B.filter(B.name("d", ELEMENT), elements("T"),
                        B.operApply(B.name("G", PREDICATE), B.name("d", ELEMENT))),
                B.param("T", TlaTypes.set(ELEMENT)), B.param("G", PREDICATE));
        var pattern = B.filter(B.name("e", ELEMENT), elements("S"), B.operApply(B.name("P", PREDICATE), B.name("e", ELEMENT)));
        var replacement = B.letIn(B.operApply(B.name("Keep", keepType), elements("S"), B.name("P", PREDICATE)), keep);
        assertRejected("the operator parameter P must be applied in the replacement",
                () -> library(rule("PassOn", B.eql(pattern, replacement),
                        B.param("S", TlaTypes.set(ELEMENT)), B.param("P", PREDICATE))));
    }

    /**
     * A LET definition body starts a new label scope, and beta reduction moves the arguments of a
     * higher-order parameter under the binders of its body.
     */
    @Test
    void precomputesWhereALabelOfABindingIsInvalid() {
        var library = library(letConst(), inFilter(), forallSwap(), commuteAnd());
        assertEquals(Set.of("x"), find(library, "LetConst").unlabelled());
        assertEquals(Set.of("x", "P"), find(library, "InFilter").unlabelled(),
                "P applies to x, which no binder of the replacement introduces");
        assertEquals(Set.of("S", "P"), find(library, "ForallSwap").unlabelled());
        assertEquals(Set.of(), find(library, "CommuteAnd").unlabelled());
    }

    /** A formula may keep an assignment, but must not become one: an IF condition is a guard. */
    @Test
    void aFormulaThatDidNotAssignInThePatternMustNotAssignInTheReplacement() {
        var library = library(inFilter(), ifAsAndOr());
        assertFalse(find(library, "InFilter").assignments().admits(Set.of("P")),
                "the filter's body would become a conjunct");
        var conditional = find(library, "IfAsAndOr").assignments();
        assertTrue(conditional.admits(Set.of("Q", "R")), "each branch stays an assigning disjunct, in order");
        assertFalse(conditional.admits(Set.of("P")), "the condition is copied and becomes a conjunct");
    }

    /**
     * Apalache requires every branch of a disjunction or an IF to assign every variable, so a branch
     * of the replacement must hold the primed formulas of some branch of the pattern.
     */
    @Test
    void everyBranchOfTheReplacementAssignsAsABranchOfThePattern() {
        var library = library(orFalse(), ifTrueBool(), ifAsAndOr());
        for (var unbalanced : List.of("OrFalse", "IfTrueBool")) {
            var facts = find(library, unbalanced).assignments();
            assertTrue(facts.admits(Set.of()), unbalanced + " applies to a guard");
            assertFalse(facts.admits(Set.of("P")), unbalanced + " adds a branch that assigns nothing");
        }
        var conditional = find(library, "IfAsAndOr").assignments();
        assertEquals(Set.of(Set.of("Q"), Set.of("R")), conditional.pattern().branches());
        assertEquals(Set.of(Set.of("P", "Q"), Set.of("R")), conditional.replacement().branches());
        assertTrue(conditional.admits(Set.of("Q")), "the ELSE branch assigned nothing before either");
    }

    /**
     * Snowcat types a LET definition that reads no parameter over variables of its own, which the
     * rewriter would draw apart from the definition's application.
     */
    @Test
    void typesALetDefinitionOfTheReplacementAsTheReplacementAppliesIt() {
        var replacement = (LetInEx) find(library(letApply()), "LetApply").replacement();
        var definition = TlaExpressions.localDeclarations(replacement).getFirst();
        assertEquals(TlaTypes.operator(ELEMENT, ELEMENT), TlaTypes.typeOf(definition));
        assertEquals(ELEMENT, TlaTypes.typeOf(definition.body()));

        var other = TlaTypes.typeVariable(1);
        var own = TlaTypes.typeVariable(2);
        var pair = B.tuple(B.name("x", ELEMENT), B.name("y", other));
        var twice = B.letIn(B.tuple(B.operApply(B.name("F", TlaTypes.operator(ELEMENT, ELEMENT)), B.name("x", ELEMENT)),
                        B.operApply(B.name("F", TlaTypes.operator(other, other)), B.name("y", other))),
                B.decl("F", B.name("v", own), B.param("v", own)));
        assertRejected("apply each definition at one type",
                () -> library(rule("Twice", B.eql(pair, twice), B.param("x", ELEMENT), B.param("y", other))));
    }

    private static void assertRejected(String message, org.junit.jupiter.api.function.Executable executable) {
        var failure = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }

    private static RewriteRule find(RewriteLibrary library, String name) {
        return library.rules().stream().filter(rule -> rule.name().equals(name)).findFirst().orElseThrow();
    }

    private static Map<String, Kind> kinds(RewriteLibrary library, String name) {
        var result = new java.util.HashMap<String, Kind>();
        find(library, name).parameters().forEach(parameter -> result.put(parameter.name(), parameter.kind()));
        return result;
    }

    private static List<String> ruleNames(List<RewriteRule> rules) {
        return rules.stream().map(RewriteRule::name).toList();
    }
}
