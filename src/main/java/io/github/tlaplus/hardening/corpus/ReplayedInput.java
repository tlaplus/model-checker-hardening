package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.common.ExprEdge;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What replaying one stored input shows beyond its envelope.
 *
 * @param counts the evaluated nodes, constructs and construct edges of the checked module
 * @param temporalShapes how often each temporal shape of the module's properties occurs (ADR 0028):
 *     an edge from the enclosing temporal operator, or {@code PROPERTY}, to a temporal operator,
 *     both reading the state
 * @param appliedRules the rewrite rules a metamorphic input applied, in order; empty otherwise
 */
public record ReplayedInput(
        ExprCounts counts, SortedMap<ExprEdge, Long> temporalShapes, List<String> appliedRules) {
    public ReplayedInput {
        Objects.requireNonNull(counts, "counts");
        temporalShapes = Collections.unmodifiableSortedMap(
                new TreeMap<>(Objects.requireNonNull(temporalShapes, "temporalShapes")));
        appliedRules = List.copyOf(Objects.requireNonNull(appliedRules, "appliedRules"));
    }
}
