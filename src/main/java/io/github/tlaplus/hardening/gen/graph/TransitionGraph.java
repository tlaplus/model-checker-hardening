package io.github.tlaplus.hardening.gen.graph;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A directed graph over the nodes {@code 0..N-1}, the control structure of a graph module
 * (ADR 0023 §2). Node 0 is initial, and an edge {@code (u, v)} is one disjunct of the next-state
 * action, which runs the action of {@code u} and moves {@code pc} to {@code v}.
 *
 * @param successors the distinct successors of each node, in the order the decoder drew them
 */
@ArchRole(Role.MODEL)
@Key
public record TransitionGraph(List<List<Integer>> successors) {
    /** One edge, from {@code source} to {@code target}. */
    public record Edge(int source, int target) {}

    public TransitionGraph {
        Objects.requireNonNull(successors, "successors");
        Preconditions.require(!successors.isEmpty(), "a graph has at least one node");
        var copy = new ArrayList<List<Integer>>(successors.size());
        for (var targets : successors) {
            var immutable = List.copyOf(targets);
            Preconditions.require(new HashSet<>(immutable).size() == immutable.size(),
                    "the successors of a node are distinct");
            for (var target : immutable) {
                Preconditions.require(target >= 0 && target < successors.size(),
                        "an edge ends at a node of the graph");
            }
            copy.add(immutable);
        }
        successors = List.copyOf(copy);
    }

    /** Returns N, the number of nodes. */
    public int nodeCount() {
        return successors.size();
    }

    /** Returns the edges, ordered by source and then in successor order. */
    public List<Edge> edges() {
        var edges = new ArrayList<Edge>();
        for (var source = 0; source < successors.size(); source++) {
            for (var target : successors.get(source)) {
                edges.add(new Edge(source, target));
            }
        }
        return List.copyOf(edges);
    }

    /**
     * Returns the length of a shortest path from node 0 to {@code node}, or empty when no path
     * reaches it.
     */
    public OptionalInt distance(int node) {
        Preconditions.require(node >= 0 && node < successors.size(), "node is a node of the graph");
        var distances = distances();
        return distances[node] < 0 ? OptionalInt.empty() : OptionalInt.of(distances[node]);
    }

    /** Breadth-first search from node 0; -1 marks an unreachable node. */
    private int[] distances() {
        var distances = new int[successors.size()];
        Arrays.fill(distances, -1);
        distances[0] = 0;
        var queue = new ArrayDeque<Integer>();
        queue.add(0);
        while (!queue.isEmpty()) {
            var node = queue.remove();
            for (var target : successors.get(node)) {
                if (distances[target] < 0) {
                    distances[target] = distances[node] + 1;
                    queue.add(target);
                }
            }
        }
        return distances;
    }
}
