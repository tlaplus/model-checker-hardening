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
    void keepsAnUnmodifiableCopySortedByName() {
        var source = new HashMap<String, Long>(Map.of("SET_ENUM", 2L, "EQ", 1L));
        var counts = new ExprCounts(3, new TreeMap<>(source));
        source.put("PLUS", 4L);

        assertEquals(List.of("EQ", "SET_ENUM"), List.copyOf(counts.exprs().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> counts.exprs().put("PLUS", 4L));
    }

    @Test
    void rejectsNegativeCountsAndMissingNames() {
        assertThrows(IllegalArgumentException.class, () -> new ExprCounts(-1, new TreeMap<>()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExprCounts(1, new TreeMap<>(Map.of("EQ", -1L))));
        var nullCount = new TreeMap<String, Long>();
        nullCount.put("EQ", null);
        assertThrows(NullPointerException.class, () -> new ExprCounts(1, nullCount));
        assertThrows(NullPointerException.class, () -> new ExprCounts(1, null));
    }
}
