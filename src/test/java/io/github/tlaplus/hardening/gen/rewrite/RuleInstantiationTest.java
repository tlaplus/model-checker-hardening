package io.github.tlaplus.hardening.gen.rewrite;

import static io.github.tlaplus.hardening.gen.rewrite.RuleFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;
import org.apalache_mc.tla.jir.TlaTypeUnifier;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/** Instantiates a rule's whole formula, as an edge guard of a graph module does (ADR 0023 §6). */
class RuleInstantiationTest {
    private static final TlaEx T = B.name("T", TlaTypes.set(TlaTypes.INT));

    @Test
    void aRuleKeepsTheRelationItDeclares() {
        assertSame(TlaOperators.EQ, checked(unionSelf()).formula().oper());
        var equivalence = checked(rule("DoubleNegEquiv", B.equiv(bool("P"), B.not(B.not(bool("P")))),
                B.param("P", TlaTypes.BOOL)));
        assertSame(TlaOperators.EQUIV, equivalence.formula().oper());
        assertEquals(bool("P"), equivalence.pattern());
        assertEquals(B.not(B.not(bool("P"))), equivalence.replacement());
    }

    @Test
    void theFormulaTakesEveryValueOnBothSides() {
        var result = RuleInstantiation.instantiateFormula(checked(unionSelf()), Map.of("S", T), integerSets(), numbered());
        assertEquals(B.eql(T, B.union(T, T)), result);
        assertEquals(TlaTypes.BOOL, TlaTypes.typeOf(result));
    }

    /** Both sides bind e; each binder is renamed apart from every name in scope. */
    @Test
    void theBindersOfBothSidesAreRenamedApart() {
        var some = rule("SomeNotNone",
                B.eql(B.exists(B.name("e", ELEMENT), elements("S"), B.bool(true)),
                        B.not(B.forall(B.name("e", ELEMENT), elements("S"), B.bool(false)))),
                B.param("S", TlaTypes.set(ELEMENT)));
        var result = RuleInstantiation.instantiateFormula(checked(some), Map.of("S", T), integerSets(), numbered());
        assertEquals(java.util.Set.of("e_1"), IrNames.bound(result));
        assertFalse(IrNames.free(result).contains("e"));
    }

    /** S occurs three times in S = S \\union S, so its label is copied and renamed apart twice. */
    @Test
    void aLabelCopiedAcrossBothSidesIsRenamedApart() {
        var result = RuleInstantiation.instantiateFormula(
                checked(unionSelf()), Map.of("S", B.label(T, "lab")), integerSets(), numbered());
        assertEquals(3, IrNames.labels(result).size(), IrNames.labels(result).toString());
    }

    @Test
    void theFormulaNeedsAValueForEveryParameterAndNoOperatorParameter() {
        var missing = assertThrows(IllegalArgumentException.class, () -> RuleInstantiation.instantiateFormula(
                checked(addSub()), Map.of("x", integer("a")), TlaTypeSubstitution.empty(), numbered()));
        assertEquals("no value for the parameter y", missing.getMessage());
        var operator = assertThrows(IllegalArgumentException.class, () -> RuleInstantiation.instantiateFormula(
                checked(forallSwap()), Map.of("S", T, "T", T, "P", T), TlaTypeSubstitution.empty(), numbered()));
        assertEquals("the formula of ForallSwap has an operator parameter", operator.getMessage());
    }

    /** Instantiates the element type variable at Int. */
    private static TlaTypeSubstitution integerSets() {
        return new TlaTypeUnifier(TlaTypes.set(ELEMENT))
                .unify(Optional.of(TlaTypeSubstitution.empty()), TlaTypes.set(ELEMENT), TlaTypes.typeOf(T))
                .orElseThrow()
                .substitution();
    }

    /** Names each binder or label apart by appending a counter. */
    private static UnaryOperator<String> numbered() {
        var counter = new AtomicInteger();
        return name -> name + "_" + counter.incrementAndGet();
    }

    private static RewriteRule checked(TlaOperDecl definition) {
        return library(definition).rules().getFirst();
    }
}
