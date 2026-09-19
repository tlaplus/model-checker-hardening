package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.ShallowPattern;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What the quality gate keeps of a settled generation (ADR 0010, ADR 0013). The keys live in the
 * {@code [mutator]} table.
 *
 * @param selectFraction the upper bound on the share of a generation's gate input kept in {@code
 *     04quality-pass}
 * @param shallowPatterns the patterns that make an entry inadmissible to the gate
 * @param cellCapacity the kept entries per behaviour cell; 0 removes the bound
 * @param coverage whether an entry that adds an operator-edge coverage feature is kept
 */
public record QualityGateConfig(
        double selectFraction, Set<ShallowPattern> shallowPatterns, int cellCapacity, boolean coverage) {
    public QualityGateConfig {
        Preconditions.require(selectFraction > 0.0 && selectFraction <= 1.0,
                "selectFraction must be in the range (0, 1]");
        var patternCopy = EnumSet.noneOf(ShallowPattern.class);
        patternCopy.addAll(Objects.requireNonNull(shallowPatterns, "shallowPatterns"));
        shallowPatterns = Collections.unmodifiableSet(patternCopy);
        Preconditions.requireNonnegative(cellCapacity, "cellCapacity");
    }

    /** Returns the settings written by {@code fuzztla init}. */
    public static QualityGateConfig defaults() {
        return new QualityGateConfig(0.05, EnumSet.allOf(ShallowPattern.class), 4, true);
    }

    /** Returns whether behaviour cells are bounded. */
    public boolean boundsCells() {
        return cellCapacity > 0;
    }
}
