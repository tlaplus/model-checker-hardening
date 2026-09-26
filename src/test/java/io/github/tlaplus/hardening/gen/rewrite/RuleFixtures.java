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
