package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import java.util.List;
import java.util.Optional;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/** How the walk rewrites a module's fairness and property with temporal rules (ADR 0027 §2). */
class TemporalRewriterTest {
    private static final TlaEx X = B.name("var0", TlaTypes.INT);
    private static final TlaEx STEP = B.name(GeneratedSpec.STEP_VARIABLE, TlaTypes.INT);
    private static final TlaEx POSITIVE = B.gt(X, B.integer(0));
    private static final TlaEx INCREMENT = B.primeEq(X, B.plus(X, B.integer(1)));

    /** The base rules match nothing in these modules, so only the property reads bytes. */
    private static final RewriteLibrary BASE = library(unionSelf());

    @Test
    void aLevelRaisingRuleRewritesTheRootOfTheProperty() {
        var spec = spec(List.of(), B.always(POSITIVE));
        // Orientation, then one rewrite at the root; every later marker is even.
        var rewrite = rewriter(temporal(eventuallyWeaken())).rewriteSpec(spec, new Draw(new byte[] {0, 1, 0, 0}));
        assertEquals(List.of("EventuallyWeaken"), rewrite.appliedRules());
        var property = rewrite.rewritten().property().orElseThrow();
        assertEquals(B.and(B.always(POSITIVE), B.eventually(B.always(POSITIVE))), property.formula());
        assertEquals(spec.initPredicate(), rewrite.rewritten().initPredicate());
    }

    /** x > 0 = TRUE: the operands of = are values, where no temporal formula may stand. */
    @Test
    void aLevelRaisingRuleSkipsAValuePosition() {
        var spec = spec(List.of(), B.eql(POSITIVE, B.bool(true)));
        var ones = new byte[64];
        java.util.Arrays.fill(ones, (byte) 1);
        var rewrite = rewriter(temporal(eventuallyWeaken())).rewriteSpec(spec, new Draw(ones));
        var formula = rewrite.rewritten().property().orElseThrow().formula();
        // Only the root is a temporal position; the rewrites stack there and nowhere else.
        assertTrue(rewrite.appliedRules().stream().allMatch("EventuallyWeaken"::equals));
        assertFalse(rewrite.appliedRules().isEmpty());
        var operands = new java.util.ArrayList<TlaEx>();
        org.apalache_mc.tla.jir.TlaExpressions.forEach(formula, node -> {
            if (node instanceof at.forsyte.apalache.tla.lir.OperEx application
                    && application.oper() == org.apalache_mc.tla.jir.TlaOperators.EQ) {
                operands.addAll(org.apalache_mc.tla.jir.TlaExpressions.arguments(application));
            }
        });
        assertTrue(operands.stream().allMatch(operand -> operand.equals(POSITIVE) || operand.equals(B.bool(true))),
                "an operand of = was rewritten: " + formula);
    }

    @Test
    void theFairnessConditionsAreRewrittenBeforeTheProperty() {
        var fairness = B.weakFair(B.tuple(X), INCREMENT);
        var spec = spec(List.of(fairness), B.always(POSITIVE));
        var rewrite = rewriter(temporal(wfDef())).rewriteSpec(spec, new Draw(new byte[] {0, 1, 0, 0}));
        assertEquals(List.of("WFDef"), rewrite.appliedRules());
        var angle = B.noStutter(INCREMENT, B.tuple(X));
        assertEquals(List.of(B.or(B.always(B.eventually(B.not(B.enabled(angle)))), B.always(B.eventually(angle)))),
                rewrite.rewritten().property().orElseThrow().fairness());
    }

    /** <>(<<A>>_v \/ FALSE) is no formula, so the <<A>>_v of <><<A>>_v stays as it is. */
    @Test
    void theSubscriptedActionOfATemporalFormulaStaysWhole() {
        var spec = spec(List.of(), B.eventually(B.noStutter(INCREMENT, B.tuple(X))));
        var ones = new byte[256];
        java.util.Arrays.fill(ones, (byte) 1);
        var rewrite = new Rewriter(library(orFalse()), temporal(alwaysTwice()), IrGenerationConfig.defaults(),
                RewriteLimits.defaults()).rewriteSpec(spec, new Draw(ones));
        var formula = rewrite.rewritten().property().orElseThrow().formula();
        org.apalache_mc.tla.jir.TlaExpressions.forEach(formula, node -> {
            if (node instanceof at.forsyte.apalache.tla.lir.OperEx application
                    && application.oper() == org.apalache_mc.tla.jir.TlaOperators.EVENTUALLY) {
                var operand = org.apalache_mc.tla.jir.TlaExpressions.arguments(application).getFirst();
                assertFalse(operand instanceof at.forsyte.apalache.tla.lir.OperEx inner
                                && inner.oper() == org.apalache_mc.tla.jir.TlaOperators.OR
                                && FormulaLevels.isAction(inner),
                        "an action under <> must be <<A>>_v: " + formula);
            }
        });
        assertTrue(rewrite.appliedRules().contains("OrFalse"), "OrFalse applies at the formula: " + formula);
    }

    /** EquivAsEq would put two temporal formulas under =, which SANY rejects. */
    @Test
    void aRewriteRuleBindsATemporalFormulaOnlyWhereTheReplacementKeepsAFormula() {
        var temporal = B.equiv(B.always(POSITIVE), B.always(B.gt(X, B.integer(1))));
        assertTrue(RuleMatcher.match(library(equivAsEq()).rules().getFirst(), temporal).isEmpty());
        var state = B.equiv(POSITIVE, B.gt(X, B.integer(1)));
        assertTrue(RuleMatcher.match(library(equivAsEq()).rules().getFirst(), state).isPresent());
        assertTrue(RuleMatcher.match(library(doubleNeg()).rules().getFirst(), B.always(POSITIVE)).isPresent(),
                "~~[]P keeps []P in a formula position");
    }

    /** Without temporal rules, the walk reads no byte in the property, as before ADR 0027. */
    @Test
    void withoutTemporalRulesThePropertyStaysAsItIs() {
        var spec = spec(List.of(B.weakFair(B.tuple(X), INCREMENT)), B.always(POSITIVE));
        var plain = new Rewriter(library(doubleNeg()), IrGenerationConfig.defaults(), RewriteLimits.defaults());
        assertFalse(plain.rewritesProperties());
        var ones = new byte[256];
        java.util.Arrays.fill(ones, (byte) 1);
        var rewrite = plain.rewriteSpec(spec, new Draw(ones));
        assertEquals(spec.property(), rewrite.rewritten().property());
        var withTemporal = new Rewriter(library(doubleNeg()), temporal(eventuallyWeaken()),
                IrGenerationConfig.defaults(), RewriteLimits.defaults());
        var withoutProperty = new GeneratedSpec(spec.variables(), spec.operators(), spec.initPredicate(),
                spec.nextAction(), spec.invariant(), Optional.empty(), spec.stepBound());
        assertEquals(plain.rewriteSpec(withoutProperty, new Draw(ones)),
                withTemporal.rewriteSpec(withoutProperty, new Draw(ones)),
                "a module without a property decodes alike with temporal rules");
    }

    /** A rewrite payload is short, so the property reads it first (ADR 0027 §2). */
    @Test
    void thePropertyIsRewrittenBeforeTheOtherBodies() {
        var spec = spec(List.of(), B.always(POSITIVE));
        var rewriter = new Rewriter(library(doubleNeg()), temporal(alwaysUnfold()),
                IrGenerationConfig.defaults(), RewriteLimits.defaults());
        // Orientation, then at the root of the property: rewrite, slot 1 of DoubleNeg and AlwaysUnfold.
        var rewrite = rewriter.rewriteSpec(spec, new Draw(new byte[] {0, 1, 0, 1}));
        assertEquals(List.of("AlwaysUnfold"), rewrite.appliedRules());
        assertEquals(B.and(POSITIVE, B.always(POSITIVE)), rewrite.rewritten().property().orElseThrow().formula());
    }

    private static Rewriter rewriter(RewriteLibrary temporal) {
        return new Rewriter(BASE, temporal, IrGenerationConfig.defaults(), RewriteLimits.defaults());
    }

    /** var0 counts up from 0 for three steps; the invariant is TRUE. */
    private static GeneratedSpec spec(List<TlaEx> fairness, TlaEx formula) {
        return new GeneratedSpec(
                List.of(TlaDeclarations.variable("var0", TlaTypes.INT),
                        TlaDeclarations.variable(GeneratedSpec.STEP_VARIABLE, TlaTypes.INT)),
                List.of(), B.and(B.eql(X, B.integer(0)), B.eql(STEP, B.integer(0))),
                B.and(INCREMENT, B.primeEq(STEP, B.plus(STEP, B.integer(1)))),
                B.bool(true), Optional.of(new TemporalProperty(fairness, formula)), 3);
    }
}
