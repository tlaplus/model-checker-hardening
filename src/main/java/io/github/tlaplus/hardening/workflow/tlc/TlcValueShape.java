package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.common.Preconditions;
import tlc2.value.IValue;
import tlc2.value.impl.Enumerable;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;

/**
 * The shape of the values of one state: how many value nodes they hold, their largest collection,
 * and their deepest nesting.
 *
 * <p>Every value is one node. A record, tuple or explicit function adds its fields, elements, or
 * domain and range values; another enumerable value adds its elements. Any other value, including a
 * lazy function, is a leaf. The walk stops at a node cap, after which the three measurements are
 * lower bounds and {@link #saturated()} holds, so a large state cannot make measurement unbounded.
 */
final class TlcValueShape {
    private final long nodeCap;
    private long nodes;
    private long cardinality;
    private long nesting;
    private boolean saturated;

    TlcValueShape(long nodeCap) {
        Preconditions.require(nodeCap > 0, "nodeCap must be positive");
        this.nodeCap = nodeCap;
    }

    /** Adds one top-level value, such as the value of a state variable. */
    void add(IValue value) {
        walk(value, 0);
    }

    long nodes() {
        return nodes;
    }

    long cardinality() {
        return cardinality;
    }

    long nesting() {
        return nesting;
    }

    boolean saturated() {
        return saturated;
    }

    private void walk(IValue value, long depth) {
        if (nodes >= nodeCap) {
            saturated = true;
            return;
        }
        nodes++;
        nesting = Math.max(nesting, depth);
        switch (value) {
            case RecordValue record -> children(record.values, depth);
            case TupleValue tuple -> children(tuple.elems, depth);
            case FcnRcdValue function -> {
                function.normalize();
                if (function.domain != null) {
                    children(function.domain, depth);
                }
                children(function.values, depth);
                cardinality = Math.max(cardinality, function.values.length);
            }
            case Enumerable enumerable -> elements(enumerable, depth);
            default -> {
                // A scalar or a lazily represented value.
            }
        }
    }

    private void children(Value[] values, long depth) {
        cardinality = Math.max(cardinality, values.length);
        for (var child : values) {
            walk(child, depth + 1);
        }
    }

    private void elements(Enumerable enumerable, long depth) {
        try {
            cardinality = Math.max(cardinality, enumerable.size());
            var elements = enumerable.elements();
            Value element;
            while (!saturated && (element = elements.nextElement()) != null) {
                walk(element, depth + 1);
            }
        } catch (RuntimeException exception) {
            // TLC refuses to enumerate a value it considers too large; measure what was seen.
            saturated = true;
        }
    }
}
