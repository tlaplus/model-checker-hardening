package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.gen.Draw;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Section order and weights are the stored byte encoding of a graph input (ADR 0023 §3). */
class GraphSectionTest {
    @Test
    void layoutOrderAndWeightsAreTheStoredByteEncoding() {
        assertEquals(Map.of("VARIABLES", 1, "AUXILIARY_OPERATORS", 2, "INIT", 2, "EDGES", 2, "NODES", 8, "GUARDS", 0),
                weights(false));
        assertEquals(Map.of("VARIABLES", 1, "AUXILIARY_OPERATORS", 2, "INIT", 2, "EDGES", 2, "NODES", 8, "GUARDS", 4),
                weights(true));
        assertEquals(List.of("VARIABLES", "AUXILIARY_OPERATORS", "INIT", "EDGES", "NODES", "GUARDS"),
                Arrays.stream(GraphSection.values()).map(Enum::name).toList());
    }

    /** Without guards, the slices are those of a graph input decoded before guards existed. */
    @Test
    void sectionsAreProportionalAndTheNodesTakeTheRemainder() {
        assertEquals(List.of(10, 20, 20, 20, 81, 0), lengths(151, false));
    }

    @Test
    void theGuardsTakeTheRemainderWhenTheyArePresent() {
        assertEquals(List.of(7, 15, 15, 15, 63, 36), lengths(151, true));
    }

    private static Map<String, Integer> weights(boolean guarded) {
        var layout = new LinkedHashMap<String, Integer>();
        for (var section : GraphSection.values()) {
            layout.put(section.name(), section.weight(guarded));
        }
        return layout;
    }

    private static List<Integer> lengths(int bytes, boolean guarded) {
        var sections = GraphSection.split(new Draw(new byte[bytes]), guarded);
        return Arrays.stream(GraphSection.values()).map(section -> sections.get(section).remaining()).toList();
    }
}
