package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Objects;

/**
 * What a {@link Ranking} reads of one entry: TLC's exploration metrics and the counts that replaying
 * the input yields.
 *
 * @param metrics TLC's exploration metrics of the entry
 * @param temporalShapes the distinct temporal shapes of the entry's property (ADR 0028 §2); 0 when
 *     the ranking does not replay the entry or the replay failed
 */
public record RankingInput(ExplorationMetrics metrics, long temporalShapes) {
    public RankingInput {
        Objects.requireNonNull(metrics, "metrics");
        Preconditions.requireNonnegative(temporalShapes, "temporalShapes");
    }
}
