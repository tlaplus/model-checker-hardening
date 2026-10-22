package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Draw;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The independent parts of a graph input after its header, each decoded from its own contiguous
 * range of bytes (ADR 0023 §3), as {@link ModuleSection} does for a module input.
 *
 * <p>Declaration order is the byte layout and the draw order, and each constant's weight is its
 * share of the input. Both are part of the byte encoding: {@code GraphSectionTest} pins them.
 */
enum GraphSection {
    /** Data-variable types. */
    VARIABLES(1),
    /** State-free definitions that every later body may apply. */
    AUXILIARY_OPERATORS(2),
    /** The initial values of the data variables. */
    INIT(2),
    /**
     * The successor lists, in equal slices, one per node. They have a section of their own, apart
     * from the node actions, because an action's expressions would otherwise consume the bytes its
     * successors follow, and because a mutation of an action then leaves the graph unchanged.
     */
    EDGES(2),
    /** The node actions, in equal slices, one per node. */
    NODES(8);

    private final int weight;

    GraphSection(int weight) {
        this.weight = weight;
    }

    /** Returns this section's share of the input, relative to the sum over all sections. */
    int weight() {
        return weight;
    }

    /** Divides every remaining byte of {@code draw} among the sections, in declaration order. */
    static Map<GraphSection, Draw> split(Draw draw) {
        var sections = List.of(values());
        var slices = WeightedSlices.split(draw, Arrays.stream(values()).map(GraphSection::weight).toList());
        var result = new EnumMap<GraphSection, Draw>(GraphSection.class);
        for (var index = 0; index < sections.size(); index++) {
            result.put(sections.get(index), slices.get(index));
        }
        return result;
    }
}
