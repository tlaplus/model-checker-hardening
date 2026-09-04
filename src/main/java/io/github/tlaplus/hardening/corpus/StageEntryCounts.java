package io.github.tlaplus.hardening.corpus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** How many entries one stage has produced under each verdict. */
public record StageEntryCounts(Map<CorpusVerdict, Long> verdicts) {
    public StageEntryCounts {
        Objects.requireNonNull(verdicts, "verdicts");
        var copy = new EnumMap<CorpusVerdict, Long>(CorpusVerdict.class);
        for (var verdict : CorpusVerdict.values()) {
            var count = verdicts.getOrDefault(verdict, 0L);
            if (count < 0) {
                throw new IllegalArgumentException("corpus counters must be nonnegative");
            }
            copy.put(verdict, count);
        }
        verdicts = Map.copyOf(copy);
    }

    /** Returns the counts of a stage that has produced no verdict yet. */
    public static StageEntryCounts empty() {
        return new StageEntryCounts(Map.of());
    }

    public long count(CorpusVerdict verdict) {
        return verdicts.get(Objects.requireNonNull(verdict, "verdict"));
    }

    /** Returns the number of entries this stage has produced a verdict for. */
    public long processed() {
        return verdicts.values().stream().mapToLong(Long::longValue).sum();
    }
}
