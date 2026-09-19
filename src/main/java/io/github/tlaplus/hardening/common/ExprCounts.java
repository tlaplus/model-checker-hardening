package io.github.tlaplus.hardening.common;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The size of a piece of TLA+ code, the occurrences of each expression construct in it, and the
 * occurrences of each edge between constructs.
 *
 * @param nodes the subexpressions counted
 * @param exprs the occurrences of each construct, keyed by construct name
 * @param edges the occurrences of each edge between constructs
 */
public record ExprCounts(long nodes, SortedMap<String, Long> exprs, SortedMap<ExprEdge, Long> edges) {
    public ExprCounts {
        Preconditions.requireNonnegative(nodes, "nodes");
        exprs = occurrences(exprs, "exprs");
        edges = occurrences(edges, "edges");
    }

    private static <K> SortedMap<K, Long> occurrences(SortedMap<K, Long> source, String name) {
        var copy = new TreeMap<K, Long>();
        Objects.requireNonNull(source, name).forEach((key, occurrences) -> {
            Objects.requireNonNull(key, "key");
            Preconditions.requireNonnegative(
                    Objects.requireNonNull(occurrences, "occurrences"), String.valueOf(key));
            copy.put(key, occurrences);
        });
        return Collections.unmodifiableSortedMap(copy);
    }
}
