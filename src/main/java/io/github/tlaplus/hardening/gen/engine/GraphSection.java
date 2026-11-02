package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Draw;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The independent parts of a graph input after its header, each decoded from its own contiguous
 * range of bytes (ADR 0023 §3), as {@link ModuleSection} does for a module input.
 *
 * <p>Declaration order is the byte layout, and each constant's weight is its share of the input.
 * Both are part of the byte encoding: {@code GraphSectionTest} pins them. An optional section has
 * weight 0 when the decoder does not use it, so its absence leaves the other slices unchanged; the
 * optional sections come last for the same reason.
 */
enum GraphSection {
    /** Data-variable types. */
    VARIABLES(1, options -> true),
    /** State-free definitions that every later body may apply. */
    AUXILIARY_OPERATORS(2, options -> true),
    /** The initial values of the data variables. */
    INIT(2, options -> true),
    /**
     * The successor lists, read as one cursor in node order. They have a section of their own,
     * apart from the node actions, because an action's expressions would otherwise consume the
     * bytes its successors follow, and because a mutation of an action then leaves the graph
     * unchanged.
     */
    EDGES(2, options -> true),
    /** The node actions, in slices filled in node order, one per node. */
    NODES(8, options -> true),
    /**
     * The tautological guards of the edges, read as one cursor in edge order (ADR 0023 §6). Present
     * only when the decoder has a rule library, so a graph input without guards decodes as it did
     * before guards existed.
     */
    GUARDS(4, Options::guarded),
    /**
     * The temporal property and its fairness conditions over the edges (ADR 0027 §8). Present only
     * with {@code [graph] properties}, so a graph input without them decodes as it did before.
     */
    PROPERTY(3, Options::properties);

    /**
     * Which optional sections a decoder uses.
     *
     * @param guarded whether the decoder draws edge guards
     * @param properties whether the decoder draws a property
     */
    record Options(boolean guarded, boolean properties) {}

    private final int weight;
    private final Predicate<Options> present;

    GraphSection(int weight, Predicate<Options> present) {
        this.weight = weight;
        this.present = present;
    }

    /**
     * Returns this section's share of the input, relative to the sum over all sections: 0 for an
     * optional section the decoder does not use.
     */
    int weight(Options options) {
        return present.test(options) ? weight : 0;
    }

    /** Divides every remaining byte of {@code draw} among the sections, in declaration order. */
    static Map<GraphSection, Draw> split(Draw draw, Options options) {
        var sections = List.of(values());
        var slices = WeightedSlices.split(
                draw, Arrays.stream(values()).map(section -> section.weight(options)).toList());
        var result = new EnumMap<GraphSection, Draw>(GraphSection.class);
        for (var index = 0; index < sections.size(); index++) {
            result.put(sections.get(index), slices.get(index));
        }
        return result;
    }
}
