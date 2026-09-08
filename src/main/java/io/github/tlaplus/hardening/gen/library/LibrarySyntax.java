package io.github.tlaplus.hardening.gen.library;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static io.github.tlaplus.hardening.gen.ExpressionCategory.*;

/** Supported imported IR syntax, its capabilities, and lexical binding layout. */
enum LibrarySyntax {
    LOGIC(Set.of(BOOL_LOGIC), Binding.NONE, "EQ", "NE", "AND", "OR", "NOT", "IMPLIES", "EQUIV"),
    MATH(Set.of(ARITHMETIC), Binding.NONE, "PLUS", "UNARY_MINUS", "MINUS", "MULT", "DIV", "MOD", "POW", "LT", "GT", "LE", "GE"),
    SETS(Set.of(SET), Binding.NONE, "SET_ENUM", "SET_IN", "SET_NOT_IN", "SET_UNION2", "SET_INTERSECT", "SET_SUBSET_EQ", "SET_MINUS", "SET_POWERSET", "SET_UNARY_UNION", "INT_RANGE"),
    FILTER(Set.of(SET), Binding.SINGLE_BOUNDED, "SET_FILTER"),
    MAP(Set.of(SET), Binding.MULTIPLE, "SET_MAP"),
    QUANTIFIED(Set.of(QUANTIFIER, SET), Binding.SINGLE_BOUNDED, "FORALL3", "EXISTS3", "CHOOSE3"),
    UNBOUNDED(Set.of(UNBOUND), Binding.SINGLE_UNBOUNDED, "FORALL2", "EXISTS2", "CHOOSE2"),
    CONTROL_FLOW(Set.of(CONTROL), Binding.NONE, "CASE", "CASE_OTHER", "IF_THEN_ELSE"),
    LABELLED(Set.of(LABEL), Binding.NONE, "LABEL"),
    APPLICATION(Set.of(OPERATOR), Binding.NONE, "OPER_APP"),
    FINITE(Set.of(FINITE_SET, SET), Binding.NONE, "FiniteSets!IsFiniteSet", "FiniteSets!Cardinality"),
    SEQUENCES(Set.of(SEQUENCE), Binding.NONE, "Sequences!Head", "Sequences!Tail", "Sequences!Append", "Sequences!Concat", "Sequences!Len", "Sequences!SubSeq", "Sequences!SelectSeq"),
    SEQUENCE_SET(Set.of(SEQUENCE, SET), Binding.NONE, "Sequences!Seq"),
    RECORDS(Set.of(RECORD), Binding.NONE, "RECORD"),
    RECORD_SETS(Set.of(RECORD, SET), Binding.NONE, "RECORD_SET"),
    PRODUCT(Set.of(TUPLE, SET), Binding.NONE, "SET_TIMES"),
    FUNCTION_SET(Set.of(FUNCTION, SET), Binding.NONE, "FUN_SET"),
    FUNCTION_CTOR(Set.of(FUNCTION, SET), Binding.MULTIPLE, "FUN_CTOR"),
    DOMAIN_OF(Set.of(FUNCTION, SET), Binding.NONE, "DOMAIN"),
    // Application, EXCEPT and tuple syntax have type-dependent capabilities.
    STRUCTURAL(Set.of(), Binding.NONE, "FUN_APP", "EXCEPT"),
    TUPLE_VALUE(Set.of(), Binding.NONE, "TUPLE"),
    VARIANTS(Set.of(VARIANT), Binding.NONE, "Variants!Variant", "Variants!VariantTag", "Variants!VariantGetOrElse", "Variants!VariantGetUnsafe"),
    VARIANT_FILTER(Set.of(VARIANT, SET), Binding.NONE, "Variants!VariantFilter"),
    FOLDS(Set.of(FOLD, OPERATOR), Binding.NONE, "Apalache!ApaFoldSet", "Apalache!ApaFoldSeqLeft");

    enum Binding { NONE, SINGLE_BOUNDED, SINGLE_UNBOUNDED, MULTIPLE }

    private static final Map<String, LibrarySyntax> BY_NAME = index();
    final Set<ExpressionCategory> categories;
    final Binding binding;
    private final String[] names;

    LibrarySyntax(Set<ExpressionCategory> categories, Binding binding, String... names) {
        this.categories = categories;
        this.binding = binding;
        this.names = names;
    }

    static LibrarySyntax of(TlaOper operator) {
        var name = operator.name();
        var syntax = BY_NAME.get(name);
        if (syntax == null) {
            throw new IllegalArgumentException("unsupported custom operator syntax: " + name
                    + " (action, temporal, recursive and tool-specific operators are not supported)");
        }
        return syntax;
    }

    private static Map<String, LibrarySyntax> index() {
        var result = new HashMap<String, LibrarySyntax>();
        for (var syntax : values()) {
            for (var name : syntax.names) {
                if (result.put(name, syntax) != null) {
                    throw new ExceptionInInitializerError("duplicate imported syntax " + name);
                }
            }
        }
        return Map.copyOf(result);
    }
}
