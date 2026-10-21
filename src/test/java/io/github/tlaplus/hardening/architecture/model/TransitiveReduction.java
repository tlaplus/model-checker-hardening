package io.github.tlaplus.hardening.architecture.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The arcs of a directed graph that a path through other nodes also connects. The atlas omits
 * them from its graphs of modules and packages, which leaves the transitive reduction of an
 * acyclic graph.
 */
public final class TransitiveReduction {
    /** One arc of the graph. */
    public record Arc<T>(T from, T to) {}

    private TransitiveReduction() {}

    public static <T> Set<Arc<T>> implied(Collection<Arc<T>> arcs) {
        var successors = new HashMap<T, List<T>>();
        for (var arc : arcs) {
            successors.computeIfAbsent(arc.from(), n -> new ArrayList<>()).add(arc.to());
        }
        var implied = new HashSet<Arc<T>>();
        for (var arc : arcs) {
            if (reachesIndirectly(successors, arc)) {
                implied.add(arc);
            }
        }
        return implied;
    }

    private static <T> boolean reachesIndirectly(Map<T, List<T>> successors, Arc<T> arc) {
        var seen = new HashSet<T>(Set.of(arc.from()));
        var pending = new ArrayDeque<T>();
        for (var next : successors.getOrDefault(arc.from(), List.of())) {
            if (!next.equals(arc.to())) {
                pending.add(next);
            }
        }
        while (!pending.isEmpty()) {
            var current = pending.poll();
            if (current.equals(arc.to())) {
                return true;
            }
            if (seen.add(current)) {
                pending.addAll(successors.getOrDefault(current, List.of()));
            }
        }
        return false;
    }
}
