package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/** The rule contract and matching of temporal rules (ADR 0027 §1). */
class TemporalRulesTest {
    private static final TlaEx X = integer("x");
    private static final TlaEx SUBSCRIPT = B.tuple(integer("x"));
    private static final TlaEx INCREMENT = B.primeEq(integer("x"), B.plus(integer("x"), B.integer(1)));

    @Test
    void aRewriteRuleModuleRejectsATemporalRule() {
        var failure = assertThrows(IllegalArgumentException.class, () -> library(alwaysTwice()));
        assertTrue(failure.getMessage().contains("belongs in metamorphic.temporal_rules"), failure.getMessage());
    }

    @Test
    void aTemporalRuleIsAnEquivalence() {
        var equation = rule("AlwaysTwiceEq", B.eql(B.always(FORMULA_F), B.always(B.always(FORMULA_F))),
                B.param("F", TlaTypes.BOOL));
        var failure = assertThrows(IllegalArgumentException.class, () -> temporal(equation));
        assertTrue(failure.getMessage().contains("the body of a temporal rule must be A <=> B"), failure.getMessage());
    }

    @Test
    void classifiesFormulaAndActionParameters() {
        var library = temporal(alwaysUnfold(), wfDef(), eventuallyWeaken(), leadsToDef());
        assertEquals(new RuleLevels(Set.of("F"), false, Set.of("F")), levels(library, "AlwaysUnfold"));
        assertEquals(new RuleLevels(Set.of(), false, Set.of()), levels(library, "WFDef"),
                "A is an action, held only as the action of <<A>>_v, and v no formula");
        assertEquals(new RuleLevels(Set.of("F"), true, Set.of("F")), levels(library, "EventuallyWeaken"));
        assertEquals(new RuleLevels(Set.of("F", "G"), false, Set.of("F", "G")), levels(library, "LeadsToDef"),
                "a temporal pattern does not raise the level, whatever operators the replacement uses");
        assertEquals(new RuleLevels(Set.of(), false, Set.of("P")), library(doubleNeg()).rules().getFirst().levels(),
                "a rewrite rule binds actions, and ~~P holds P where a temporal formula may stand");
    }

    @Test
    void rejectsAParameterThatIsBothAnActionAndAFormula() {
        var mixed = rule("Mixed", B.equiv(B.and(B.weakFair(SUBSCRIPT_V, ACTION_A), ACTION_A),
                        B.and(ACTION_A, B.weakFair(SUBSCRIPT_V, ACTION_A))),
                B.param("A", TlaTypes.BOOL), B.param("v", ELEMENT));
        var failure = assertThrows(IllegalArgumentException.class, () -> temporal(mixed));
        assertTrue(failure.getMessage().contains("both as an action and as a formula"), failure.getMessage());
    }

    @Test
    void aFormulaParameterDoesNotBindAnAction() {
        var always = B.always(B.gt(X, B.integer(0)));
        assertEquals(B.and(B.gt(X, B.integer(0)), always), rewrite(alwaysUnfold(), always));
        var boxAction = B.always(B.stutter(INCREMENT, SUBSCRIPT));
        assertTrue(RuleMatcher.match(checked(alwaysUnfold()), boxAction).isEmpty(),
                "[A]_v /\\ [][A]_v mixes an action into a temporal formula");
    }

    /** An action inside [][A]_v assigns only within itself, so a formula around it may move. */
    @Test
    void aFormulaParameterBindsAFormulaWithAnActionInside() {
        var inner = B.and(B.gt(X, B.integer(0)), B.always(B.stutter(INCREMENT, SUBSCRIPT)));
        var always = B.always(inner);
        assertEquals(B.and(inner, always), rewrite(alwaysUnfold(), always));
    }

    /** WFDef copies A into two actions of its own, where A assigns in each. */
    @Test
    void anActionParameterKeepsItsAssignmentsInEachAction() {
        var fairness = B.weakFair(SUBSCRIPT, INCREMENT);
        var angle = B.noStutter(INCREMENT, SUBSCRIPT);
        assertEquals(B.or(B.always(B.eventually(B.not(B.enabled(angle)))), B.always(B.eventually(angle))),
                rewrite(wfDef(), fairness));
        var box = B.always(B.stutter(INCREMENT, SUBSCRIPT));
        assertEquals(B.always(B.stutter(B.or(INCREMENT, B.unchanged(SUBSCRIPT)), SUBSCRIPT)),
                rewrite(boxActionUnchanged(), box));
        assertTrue(checked(wfDef()).assignments().admits(Set.of("A")));
    }

    @Test
    void theTemporalRulesFollowTheRewriteRules() {
        var combined = library(plusZero()).followedBy(temporal(alwaysTwice()));
        assertEquals(List.of("PlusZero", "AlwaysTwice"), ruleNames(combined.rules()));
        var duplicate = rule("PlusZero", B.equiv(B.always(FORMULA_F), B.always(B.always(FORMULA_F))),
                B.param("F", TlaTypes.BOOL));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> library(plusZero()).followedBy(temporal(duplicate)));
        assertTrue(failure.getMessage().contains("PlusZero is defined in both rule modules"), failure.getMessage());
    }

    @Test
    void graphGuardsExcludeTemporalRules() {
        assertFalse(RuleContract.isStateLevel(checked(alwaysTwice())));
        assertTrue(RuleContract.isStateLevel(checked(doubleNeg())));
    }

    private static RewriteRule checked(TlaOperDecl definition) {
        return temporalOrRewrite(definition).rules().getFirst();
    }

    /** A rule of whichever family admits it, as its module would load it. */
    private static RewriteLibrary temporalOrRewrite(TlaOperDecl definition) {
        try {
            return library(definition);
        } catch (IllegalArgumentException temporalRule) {
            return temporal(definition);
        }
    }

    private static TlaEx rewrite(TlaOperDecl definition, TlaEx node) {
        var match = RuleMatcher.match(checked(definition), node).orElseThrow();
        return RuleInstantiation.instantiate(match, Map.of(), match.types(), name -> name);
    }

    private static RuleLevels levels(RewriteLibrary library, String rule) {
        return library.rules().stream().filter(candidate -> candidate.name().equals(rule)).findFirst().orElseThrow()
                .levels();
    }

    private static List<String> ruleNames(List<RewriteRule> rules) {
        return rules.stream().map(RewriteRule::name).toList();
    }
}
