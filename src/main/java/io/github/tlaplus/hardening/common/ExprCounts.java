package io.github.tlaplus.hardening.common;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The size of a piece of TLA+ code and the occurrences of each expression construct in it.
 *
 * @param nodes the subexpressions counted
 * @param exprs the occurrences of each construct, keyed by construct name
 */
public record ExprCounts(long nodes, SortedMap<String, Long> exprs) {
    public ExprCounts {
        Preconditions.requireNonnegative(nodes, "nodes");
        var copy = new TreeMap<String, Long>();
        Objects.requireNonNull(exprs, "exprs").forEach((name, occurrences) -> {
            Objects.requireNonNull(name, "name");
            Preconditions.requireNonnegative(Objects.requireNonNull(occurrences, "occurrences"), name);
            copy.put(name, occurrences);
        });
        exprs = Collections.unmodifiableSortedMap(copy);
    }
}
