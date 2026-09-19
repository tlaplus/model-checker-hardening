package io.github.tlaplus.hardening.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ExprCountsTest {
    @Test
    void keepsUnmodifiableCopiesSortedByName() {
        var source = new HashMap<String, Long>(Map.of("SET_ENUM", 2L, "EQ", 1L));
        var edgeSource = new HashMap<ExprEdge, Long>(Map.of(
                new ExprEdge("EQ", "SET_ENUM"), 2L, new ExprEdge("AND", "EQ"), 1L));
        var counts = new ExprCounts(3, new TreeMap<>(source), new TreeMap<>(edgeSource));
        source.put("PLUS", 4L);
        edgeSource.put(new ExprEdge("AND", "PLUS"), 1L);

        assertEquals(List.of("EQ", "SET_ENUM"), List.copyOf(counts.exprs().keySet()));
        assertEquals(
                List.of(new ExprEdge("AND", "EQ"), new ExprEdge("EQ", "SET_ENUM")),
                List.copyOf(counts.edges().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> counts.exprs().put("PLUS", 4L));
        assertThrows(
                UnsupportedOperationException.class,
                () -> counts.edges().put(new ExprEdge("AND", "PLUS"), 1L));
    }

    @Test
    void rejectsNegativeCountsAndMissingNames() {
        var noEdges = new TreeMap<ExprEdge, Long>();
        assertThrows(IllegalArgumentException.class, () -> new ExprCounts(-1, new TreeMap<>(), noEdges));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExprCounts(1, new TreeMap<>(Map.of("EQ", -1L)), noEdges));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExprCounts(1, new TreeMap<>(), new TreeMap<>(Map.of(new ExprEdge("EQ", "EQ"), -1L))));
        var nullCount = new TreeMap<String, Long>();
        nullCount.put("EQ", null);
        assertThrows(NullPointerException.class, () -> new ExprCounts(1, nullCount, noEdges));
        assertThrows(NullPointerException.class, () -> new ExprCounts(1, null, noEdges));
        assertThrows(NullPointerException.class, () -> new ExprCounts(1, new TreeMap<>(), null));
        assertThrows(NullPointerException.class, () -> new ExprEdge(null, "EQ"));
    }
}
