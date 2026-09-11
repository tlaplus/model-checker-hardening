package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.RecRowT1;
import at.forsyte.apalache.tla.lir.RowT1;
import at.forsyte.apalache.tla.lir.TlaType1;
import at.forsyte.apalache.tla.lir.VarT1;
import at.forsyte.apalache.tla.lir.VariantT1;
import java.util.ArrayList;
import java.util.Objects;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * A type in Apalache's syntax whose type variables are wildcards.
 *
 * <p>Matching is one-way: a type variable of the pattern binds to the corresponding part of the
 * actual type, and the same variable must bind to equal types throughout one alternative. A row
 * that ends in a type variable matches a record or variant with the listed fields and any others;
 * the variable binds to the remaining row. A pattern without type variables matches only an equal
 * type.
 */
record TypePattern(TlaType1 pattern) {
    TypePattern {
        Objects.requireNonNull(pattern, "pattern");
    }

    /**
     * Parses a type pattern.
     *
     * @throws IllegalArgumentException if {@code text} is not a type
     */
    static TypePattern parse(String text) {
        return new TypePattern(Type1Syntax.parse(text));
    }

    boolean matches(TlaType1 actual, Bindings bindings) {
        return match(pattern, Objects.requireNonNull(actual, "actual"), bindings);
    }

    private static boolean match(TlaType1 pattern, TlaType1 actual, Bindings bindings) {
        if (pattern instanceof VarT1 variable) {
            return bindings.bindType(variable, actual);
        }
        if (TlaTypes.usedVariables(pattern).isEmpty()) {
            return pattern.equals(actual);
        }
        if (pattern.getClass() != actual.getClass()) {
            return false;
        }
        return switch (pattern) {
            case RecRowT1 record -> matchRow(record.row(), ((RecRowT1) actual).row(), bindings);
            case VariantT1 variant -> matchRow(variant.row(), ((VariantT1) actual).row(), bindings);
            case RowT1 row -> matchRow(row, (RowT1) actual, bindings);
            default -> matchChildren(pattern, actual, bindings);
        };
    }

    private static boolean matchChildren(TlaType1 pattern, TlaType1 actual, Bindings bindings) {
        var expected = TlaTypes.children(pattern);
        var found = TlaTypes.children(actual);
        if (expected.size() != found.size()) {
            return false;
        }
        for (var index = 0; index < expected.size(); index++) {
            if (!match(expected.get(index), found.get(index), bindings)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchRow(RowT1 pattern, RowT1 actual, Bindings bindings) {
        var expected = TlaTypes.rowFields(pattern);
        var found = TlaTypes.rowFields(actual);
        for (var field : expected.entrySet()) {
            var type = found.get(field.getKey());
            if (type == null || !match(field.getValue(), type, bindings)) {
                return false;
            }
        }
        var tail = TlaTypes.rowTail(pattern);
        var actualTail = TlaTypes.rowTail(actual);
        if (tail.isEmpty()) {
            return actualTail.isEmpty() && found.keySet().equals(expected.keySet());
        }
        var remaining = new ArrayList<NamedType>();
        for (var field : found.entrySet()) {
            if (!expected.containsKey(field.getKey())) {
                remaining.add(new NamedType(field.getKey(), field.getValue()));
            }
        }
        var fields = remaining.toArray(NamedType[]::new);
        var rest = actualTail.isPresent()
                ? TlaTypes.row(actualTail.orElseThrow(), fields)
                : TlaTypes.row(fields);
        return bindings.bindType(tail.orElseThrow(), rest);
    }
}
