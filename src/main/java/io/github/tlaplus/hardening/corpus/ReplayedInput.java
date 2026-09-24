package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.ExprCounts;
import java.util.List;
import java.util.Objects;

/**
 * What replaying one stored input shows beyond its envelope.
 *
 * @param counts the evaluated nodes, constructs and construct edges of the checked module
 * @param appliedRules the rewrite rules a metamorphic input applied, in order; empty otherwise
 */
public record ReplayedInput(ExprCounts counts, List<String> appliedRules) {
    public ReplayedInput {
        Objects.requireNonNull(counts, "counts");
        appliedRules = List.copyOf(Objects.requireNonNull(appliedRules, "appliedRules"));
    }
}
