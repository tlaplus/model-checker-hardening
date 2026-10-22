package io.github.tlaplus.hardening.gen.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TransitionGraphTest {
    /** The graph of ADR 0023 §2: 0 → 1, 1 → 1, 1 → 0, and node 2 without an incoming edge. */
    private static final TransitionGraph EXAMPLE = new TransitionGraph(List.of(List.of(1), List.of(1, 0), List.of()));

    @Test
    void distancesAreShortestPathLengthsFromNodeZero() {
        assertEquals(OptionalInt.of(0), EXAMPLE.distance(0));
        assertEquals(OptionalInt.of(1), EXAMPLE.distance(1));
        assertEquals(OptionalInt.empty(), EXAMPLE.distance(2));

        var chain = new TransitionGraph(List.of(List.of(1, 3), List.of(2), List.of(3), List.of()));
        assertEquals(OptionalInt.of(2), chain.distance(2));
        assertEquals(OptionalInt.of(1), chain.distance(3), "the shorter of two paths counts");
    }

    @Test
    void edgesAreOrderedBySourceThenSuccessorOrder() {
        assertEquals(List.of(new TransitionGraph.Edge(0, 1), new TransitionGraph.Edge(1, 1),
                new TransitionGraph.Edge(1, 0)), EXAMPLE.edges());
        assertEquals(3, EXAMPLE.nodeCount());
    }

    @Test
    void rejectsMalformedGraphs() {
        assertThrows(IllegalArgumentException.class, () -> new TransitionGraph(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TransitionGraph(List.of(List.of(1))));
        assertThrows(IllegalArgumentException.class, () -> new TransitionGraph(List.of(List.of(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> EXAMPLE.distance(3));
    }
}
