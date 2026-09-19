package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.util.List;
import java.util.Objects;

/**
 * The behaviour cell of an admissible entry (ADR 0013): its TLC verdict and the components of its
 * {@link QualityKey}, each after its bucket.
 */
record QualityCell(CorpusVerdict verdict, List<Long> components) {
    QualityCell {
        Objects.requireNonNull(verdict, "verdict");
        components = List.copyOf(components);
    }

    static QualityCell of(CorpusVerdict verdict, ExplorationMetrics metrics) {
        return new QualityCell(verdict, QualityKey.components(metrics));
    }
}
