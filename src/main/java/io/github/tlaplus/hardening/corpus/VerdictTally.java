package io.github.tlaplus.hardening.corpus;

import java.util.EnumMap;
import java.util.Objects;

/** A mutable per-verdict count, built up while scanning a corpus and read as {@link StageEntryCounts}. */
final class VerdictTally {
    private final EnumMap<CorpusVerdict, Long> counts = new EnumMap<>(CorpusVerdict.class);

    void increment(CorpusVerdict verdict) {
        add(verdict, 1);
    }

    void add(CorpusVerdict verdict, long count) {
        counts.merge(Objects.requireNonNull(verdict, "verdict"), count, Long::sum);
    }

    long count(CorpusVerdict verdict) {
        return counts.getOrDefault(Objects.requireNonNull(verdict, "verdict"), 0L);
    }

    /** Returns the sum over every verdict. */
    long total() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    StageEntryCounts snapshot() {
        return new StageEntryCounts(counts);
    }
}
