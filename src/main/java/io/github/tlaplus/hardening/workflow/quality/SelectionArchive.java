package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.config.QualityGateConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The behaviour cells and coverage features of the entries the gate has kept (ADR 0013), and the
 * rule that decides whether one more entry is kept.
 *
 * <p>Features matter only when cells are bounded and feature coverage is enabled; otherwise callers may
 * pass none.
 */
final class SelectionArchive {
    private final QualityGateConfig config;
    private final Map<QualityCell, Integer> occupancy = new HashMap<>();
    private final Set<CoverageFeature> covered = new HashSet<>();

    SelectionArchive(QualityGateConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Returns whether the gate needs an entry's coverage features. */
    boolean usesCoverage() {
        return config.boundsCells() && config.featureCoverage();
    }

    /** Returns whether an entry of {@code cell} and {@code features} shows something the kept entries lack. */
    boolean admits(QualityCell cell, Set<CoverageFeature> features) {
        if (!config.boundsCells() || occupancy.getOrDefault(cell, 0) < config.cellCapacity()) {
            return true;
        }
        return usesCoverage() && !covered.containsAll(features);
    }

    /** Records a kept entry. */
    void keep(QualityCell cell, Set<CoverageFeature> features) {
        occupancy.merge(cell, 1, Integer::sum);
        if (usesCoverage()) {
            covered.addAll(features);
        }
    }
}
