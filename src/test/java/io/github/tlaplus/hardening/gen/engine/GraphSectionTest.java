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
        assertEquals(Map.of("VARIABLES", 1, "AUXILIARY_OPERATORS", 2, "INIT", 2, "EDGES", 2, "NODES", 8, "GUARDS", 0,
                        "PROPERTY", 0),
                weights(PLAIN));
        assertEquals(Map.of("VARIABLES", 1, "AUXILIARY_OPERATORS", 2, "INIT", 2, "EDGES", 2, "NODES", 8, "GUARDS", 4,
                        "PROPERTY", 3),
                weights(new GraphSection.Options(true, true)));
        assertEquals(List.of("VARIABLES", "AUXILIARY_OPERATORS", "INIT", "EDGES", "NODES", "GUARDS", "PROPERTY"),
                Arrays.stream(GraphSection.values()).map(Enum::name).toList());
    }

    /** Without guards, the slices are those of a graph input decoded before guards existed. */
    @Test
    void sectionsAreProportionalAndTheNodesTakeTheRemainder() {
        assertEquals(List.of(10, 20, 20, 20, 81, 0, 0), lengths(151, PLAIN));
    }

    /** Without properties, the slices are those of a graph input decoded before properties existed. */
    @Test
    void theGuardsTakeTheRemainderWhenTheyArePresent() {
        assertEquals(List.of(7, 15, 15, 15, 63, 36, 0), lengths(151, new GraphSection.Options(true, false)));
    }

    @Test
    void thePropertyTakesTheRemainderWhenItIsPresent() {
        assertEquals(List.of(8, 16, 16, 16, 67, 0, 28), lengths(151, new GraphSection.Options(false, true)));
    }

    private static final GraphSection.Options PLAIN = new GraphSection.Options(false, false);

    private static Map<String, Integer> weights(GraphSection.Options options) {
        var layout = new LinkedHashMap<String, Integer>();
        for (var section : GraphSection.values()) {
            layout.put(section.name(), section.weight(options));
        }
        return layout;
    }

    private static List<Integer> lengths(int bytes, GraphSection.Options options) {
        var sections = GraphSection.split(new Draw(new byte[bytes]), options);
        return Arrays.stream(GraphSection.values()).map(section -> sections.get(section).remaining()).toList();
    }
}
