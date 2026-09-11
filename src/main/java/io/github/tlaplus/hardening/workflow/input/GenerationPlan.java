package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.gen.InputKind;
import java.util.Objects;

/**
 * What one invocation's input stage generates: which input kind, from which seed, and how many
 * entries it adds with at most how many workers.
 *
 * @param maximumEntries the corpus target; the stage adds entries until the corpus holds this many
 * @param initialEntries the entries the corpus already held at startup
 * @param workerLimit the most generator workers the stage starts
 */
public record GenerationPlan(
        InputKind kind, long seed, long maximumEntries, long initialEntries, int workerLimit) {
    public GenerationPlan {
        Objects.requireNonNull(kind, "kind");
        Preconditions.requireNonnegative(initialEntries, "initialEntries");
        Preconditions.requireNonnegative(seed, "seed");
        Preconditions.requirePositive(workerLimit, "workerLimit");
    }

    /** Returns how many entries the stage still has to add. */
    long missingEntries() {
        return Math.max(0L, maximumEntries - initialEntries);
    }
}
