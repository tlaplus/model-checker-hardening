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
 * <p>Declaration order is the byte layout, and each constant's weight is its share of the input.
 * Both are part of the byte encoding: {@code GraphSectionTest} pins them. An optional section has
 * weight 0 when the decoder does not use it, so its absence leaves the other slices unchanged.
 */
enum GraphSection {
    /** Data-variable types. */
    VARIABLES(1),
    /** State-free definitions that every later body may apply. */
    AUXILIARY_OPERATORS(2),
    /** The initial values of the data variables. */
    INIT(2),
    /**
     * The successor lists, read as one cursor in node order. They have a section of their own,
     * apart from the node actions, because an action's expressions would otherwise consume the
     * bytes its successors follow, and because a mutation of an action then leaves the graph
     * unchanged.
     */
    EDGES(2),
    /** The node actions, in slices filled in node order, one per node. */
    NODES(8),
    /**
     * The tautological guards of the edges, read as one cursor in edge order (ADR 0023 §6). Present
     * only when the decoder has a rule library, so a graph input without guards decodes as it did
     * before guards existed.
     */
    GUARDS(4, true);

    private final int weight;
    private final boolean optional;

    GraphSection(int weight) {
        this(weight, false);
    }

    GraphSection(int weight, boolean optional) {
        this.weight = weight;
        this.optional = optional;
    }

    /**
     * Returns this section's share of the input, relative to the sum over all sections: 0 for an
     * optional section the decoder does not use.
     */
    int weight(boolean guarded) {
        return optional && !guarded ? 0 : weight;
    }

    /**
     * Divides every remaining byte of {@code draw} among the sections, in declaration order.
     *
     * @param guarded whether the decoder draws edge guards
     */
    static Map<GraphSection, Draw> split(Draw draw, boolean guarded) {
        var sections = List.of(values());
        var slices = WeightedSlices.split(
                draw, Arrays.stream(values()).map(section -> section.weight(guarded)).toList());
        var result = new EnumMap<GraphSection, Draw>(GraphSection.class);
        for (var index = 0; index < sections.size(); index++) {
            result.put(sections.get(index), slices.get(index));
        }
        return result;
    }
}
