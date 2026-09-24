package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.gen.rewrite.RuleParameter.Kind;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
