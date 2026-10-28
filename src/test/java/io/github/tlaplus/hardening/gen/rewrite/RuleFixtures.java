package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaType1;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.apalache_mc.tla.jir.TypedParameter;

/** The seed rules of libraries/rewrites, built as Snowcat types them (ADR 0017 probe P2). */
final class RuleFixtures {
    static final TlaTypedScopeUncheckedBuilder B = new TlaTypedScopeUncheckedBuilder();
    static final TlaType1 ELEMENT = TlaTypes.typeVariable(0);
    static final TlaType1 PREDICATE = TlaTypes.operator(TlaTypes.BOOL, ELEMENT);

    private RuleFixtures() {}

    static TlaEx integer(String name) {
        return B.name(name, TlaTypes.INT);
    }

    static TlaEx bool(String name) {
        return B.name(name, TlaTypes.BOOL);
    }

    static TlaEx elements(String name) {
        return B.name(name, TlaTypes.set(ELEMENT));
    }

    static TlaOperDecl plusZero() {
        return B.decl("PlusZero", B.eql(integer("x"), B.plus(integer("x"), B.integer(0))), B.param("x", TlaTypes.INT));
    }

    static TlaOperDecl addSub() {
        return B.decl("AddSub", B.eql(integer("x"), B.plus(integer("x"), B.minus(integer("y"), integer("y")))),
                B.param("x", TlaTypes.INT), B.param("y", TlaTypes.INT));
    }

    static TlaOperDecl doubleNeg() {
        return B.decl("DoubleNeg", B.eql(bool("P"), B.not(B.not(bool("P")))), B.param("P", TlaTypes.BOOL));
    }

    static TlaOperDecl unionSelf() {
        return B.decl("UnionSelf", B.eql(elements("S"), B.union(elements("S"), elements("S"))),
                B.param("S", TlaTypes.set(ELEMENT)));
    }

    static TlaOperDecl unchangedPrime() {
        var x = B.name("x", ELEMENT);
        return B.decl("UnchangedPrime", B.eql(B.unchanged(x), B.primeEq(B.name("x", ELEMENT), B.name("x", ELEMENT))),
                B.param("x", ELEMENT));
    }

    static TlaOperDecl forallNotExists() {
        var e = B.name("e", ELEMENT);
        var p = B.name("P", PREDICATE);
        var pattern = B.forall(e, elements("S"), B.operApply(p, B.name("e", ELEMENT)));
        var replacement = B.not(B.exists(B.name("e", ELEMENT), elements("S"),
                B.not(B.operApply(B.name("P", PREDICATE), B.name("e", ELEMENT)))));
        return B.decl("ForallNotExists", B.eql(pattern, replacement),
                B.param("S", TlaTypes.set(ELEMENT)), B.param("P", PREDICATE));
    }

    static TlaOperDecl commuteAnd() {
        return B.decl("CommuteAnd", B.eql(B.and(bool("P"), bool("Q")), B.and(bool("Q"), bool("P"))),
                B.param("P", TlaTypes.BOOL), B.param("Q", TlaTypes.BOOL));
    }

    static final TlaType1 OTHER = TlaTypes.typeVariable(1);
    static final TlaType1 RELATION = TlaTypes.operator(TlaTypes.BOOL, ELEMENT, OTHER);

    /** {@code LetApply(x) == x = LET F(v) == v IN F(x)}, with F generalized as Snowcat types it. */
    static TlaOperDecl letApply() {
        var own = TlaTypes.typeVariable(2);
        var definition = B.decl("F", B.name("v", own), B.param("v", own));
        var replacement = B.letIn(
                B.operApply(B.name("F", TlaTypes.operator(ELEMENT, ELEMENT)), B.name("x", ELEMENT)), definition);
        return B.decl("LetApply", B.eql(B.name("x", ELEMENT), replacement), B.param("x", ELEMENT));
    }

    /** {@code LetConst(x, y) == x = LET F(v) == x IN F(y)}: x moves into a definition body. */
    static TlaOperDecl letConst() {
        var definition = B.decl("F", B.name("x", ELEMENT), B.param("v", OTHER));
        var replacement = B.letIn(B.operApply(B.name("F", TlaTypes.operator(ELEMENT, OTHER)), B.name("y", OTHER)),
                definition);
        return B.decl("LetConst", B.eql(B.name("x", ELEMENT), replacement),
                B.param("x", ELEMENT), B.param("y", OTHER));
    }

    /** {@code InFilter(x, S, P(_)) == (x \in {e \in S : P(e)}) = (x \in S /\ P(x))}: P applies to x. */
    static TlaOperDecl inFilter() {
        var pattern = B.in(B.name("x", ELEMENT), B.filter(B.name("e", ELEMENT), elements("S"),
                B.operApply(B.name("P", PREDICATE), B.name("e", ELEMENT))));
        var replacement = B.and(B.in(B.name("x", ELEMENT), elements("S")),
                B.operApply(B.name("P", PREDICATE), B.name("x", ELEMENT)));
        return B.decl("InFilter", B.eql(pattern, replacement),
                B.param("x", ELEMENT), B.param("S", TlaTypes.set(ELEMENT)), B.param("P", PREDICATE));
    }

    /** {@code (\A e \in S : \A d \in T : P(e, d)) = (\A d \in T : \A e \in S : P(e, d))}. */
    static TlaOperDecl forallSwap() {
        return B.decl("ForallSwap", B.eql(nestedForall(), B.forall(B.name("d", OTHER), others("T"),
                        B.forall(B.name("e", ELEMENT), elements("S"), applyRelation()))),
                B.param("S", TlaTypes.set(ELEMENT)), B.param("T", TlaTypes.set(OTHER)), B.param("P", RELATION));
    }

    /** {@code (\A e \in S : \A d \in T : P(e, d)) = (\A t \in S \X T : P(t[1], t[2]))}. */
    static TlaOperDecl forallPair() {
        var pairs = TlaTypes.tuple(ELEMENT, OTHER);
        var first = B.funApply(B.name("t", pairs), B.integer(1));
        var second = B.funApply(B.name("t", pairs), B.integer(2));
        return B.decl("ForallPair", B.eql(nestedForall(), B.forall(B.name("t", pairs),
                        B.times(elements("S"), others("T")), B.operApply(B.name("P", RELATION), first, second))),
                B.param("S", TlaTypes.set(ELEMENT)), B.param("T", TlaTypes.set(OTHER)), B.param("P", RELATION));
    }

    /** {@code (IF P THEN Q ELSE R) = ((P /\ Q) \/ (~P /\ R))}, a law of Boolean IF only. */
    static TlaOperDecl ifAsAndOr() {
        var replacement = B.or(B.and(bool("P"), bool("Q")), B.and(B.not(bool("P")), bool("R")));
        return B.decl("IfAsAndOr", B.eql(B.ite(bool("P"), bool("Q"), bool("R")), replacement),
                B.param("P", TlaTypes.BOOL), B.param("Q", TlaTypes.BOOL), B.param("R", TlaTypes.BOOL));
    }

    /** {@code OrFalse(P) == P = (P \/ FALSE)}, whose second disjunct assigns nothing. */
    static TlaOperDecl orFalse() {
        return B.decl("OrFalse", B.eql(bool("P"), B.or(bool("P"), B.bool(false))), B.param("P", TlaTypes.BOOL));
    }

    /** {@code IfTrueBool(P, Q) == P = (IF TRUE THEN P ELSE Q)}, whose ELSE branch is fresh. */
    static TlaOperDecl ifTrueBool() {
        return B.decl("IfTrueBool", B.eql(bool("P"), B.ite(B.bool(true), bool("P"), bool("Q"))),
                B.param("P", TlaTypes.BOOL), B.param("Q", TlaTypes.BOOL));
    }

    private static TlaEx nestedForall() {
        return B.forall(B.name("e", ELEMENT), elements("S"),
                B.forall(B.name("d", OTHER), others("T"), applyRelation()));
    }

    private static TlaEx applyRelation() {
        return B.operApply(B.name("P", RELATION), B.name("e", ELEMENT), B.name("d", OTHER));
    }

    private static TlaEx others(String name) {
        return B.name(name, TlaTypes.set(OTHER));
    }

    static TlaOperDecl rule(String name, TlaEx body, TypedParameter... parameters) {
        return B.decl(name, body, parameters);
    }

    /** A library of the given rules, each at the default weight. */
    static RewriteLibrary library(TlaOperDecl... rules) {
        return RewriteLibrary.fromModule(module(rules), names(rules), Map.of());
    }

    static TlaModule module(TlaDecl... declarations) {
        return TlaModules.create("Rewrites", List.of(declarations));
    }

    static Set<String> names(TlaOperDecl... rules) {
        return Arrays.stream(rules).map(TlaOperDecl::name).collect(Collectors.toSet());
    }
}
