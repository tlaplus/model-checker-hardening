package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.StageEntryCounts;
import java.time.Duration;
import java.util.Objects;

/** Cumulative verdict counters and summed worker elapsed time for one stage. */
public record StageVerdictSummary(StageEntryCounts counts, Duration elapsed) {
    public StageVerdictSummary {
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(elapsed, "elapsed");
        Preconditions.require(!elapsed.isNegative(), "stage elapsed time must be nonnegative");
    }

    /** Returns a summary with no processed inputs and no elapsed time. */
    public static StageVerdictSummary empty() {
        return new StageVerdictSummary(StageEntryCounts.empty(), Duration.ZERO);
    }

    public long count(CorpusVerdict verdict) {
        return counts.count(verdict);
    }

    public long processed() {
        return counts.processed();
    }
}
