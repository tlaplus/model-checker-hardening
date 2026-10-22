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
        var layout = new LinkedHashMap<String, Integer>();
        for (var section : GraphSection.values()) {
            layout.put(section.name(), section.weight());
        }
        assertEquals(Map.of("VARIABLES", 1, "AUXILIARY_OPERATORS", 2, "INIT", 2, "EDGES", 2, "NODES", 8), layout);
        assertEquals(List.of("VARIABLES", "AUXILIARY_OPERATORS", "INIT", "EDGES", "NODES"),
                Arrays.stream(GraphSection.values()).map(Enum::name).toList());
    }

    @Test
    void sectionsAreProportionalAndTheNodesTakeTheRemainder() {
        var bytes = new byte[151];
        var sections = GraphSection.split(new Draw(bytes));
        assertEquals(List.of(10, 20, 20, 20, 81), Arrays.stream(GraphSection.values())
                .map(section -> sections.get(section).remaining()).toList());
    }
}
