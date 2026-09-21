package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How far one logical entry has moved through the pipeline, and the single definition of the two
 * questions generation scheduling asks of it (ADR 0010).
 *
 * <p>Startup recovery reads this from a stored envelope; a running workflow accumulates it from
 * stage events. Both ask {@link #isSettled()} and {@link #isUngated()} here, so a restart and a
 * fresh invocation cannot disagree about where a generation stands. Changing either rule is a
 * change to this record alone.
 */
public record EntryProgress(int generation, Map<CorpusStage, CorpusVerdict> verdicts) {
    public EntryProgress {
        Preconditions.requireNonnegative(generation, "generation");
        Objects.requireNonNull(verdicts, "verdicts");
        var copy = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        copy.putAll(verdicts);
        verdicts = Map.copyOf(copy);
    }

    /** Returns an entry that no stage has judged yet. */
    public static EntryProgress admitted(int generation) {
        return new EntryProgress(generation, Map.of());
    }

    /** Returns the progress a stored envelope records, as far as this copy of the entry shows it. */
    public static EntryProgress of(int generation, CorpusEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        var verdicts = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            envelope.stage(stage).ifPresent(metadata -> verdicts.put(stage, metadata.verdict()));
        }
        return new EntryProgress(generation, verdicts);
    }

    /** Returns this progress with one stage's verdict recorded. */
    public EntryProgress with(CorpusStage stage, CorpusVerdict verdict) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(verdict, "verdict");
        // EnumMap's copy constructor cannot infer the key type from an empty map.
        var updated = new EnumMap<CorpusStage, CorpusVerdict>(CorpusStage.class);
        updated.putAll(verdicts);
        updated.put(stage, verdict);
        return new EntryProgress(generation, updated);
    }

    /** Returns the verdict one stage recorded, or empty while the stage has not judged the entry. */
    public Optional<CorpusVerdict> verdict(CorpusStage stage) {
        return Optional.ofNullable(verdicts.get(Objects.requireNonNull(stage, "stage")));
    }

    /**
     * Reports whether no stage will move this entry again, so its generation no longer waits on it.
     *
     * <p>Three outcomes are terminal: the parser rejected or crashed on the entry, the aggregator
     * judged it, or every checker branch finished and at least one crashed — a crash leaves the
     * entry in that checker's directory and never reaches the aggregator.
     */
    public boolean isSettled() {
        if (verdict(CorpusStage.PARSER).filter(verdict -> verdict != CorpusVerdict.PASS).isPresent()) {
            return true;
        }
        if (verdicts.containsKey(CorpusStage.AGGREGATOR)) {
            return true;
        }
        var branches = CorpusStage.checkerBranches();
        return branches.stream().allMatch(verdicts::containsKey)
                && branches.stream().anyMatch(branch -> verdicts.get(branch) == CorpusVerdict.CRASH);
    }

    /** Reports whether the aggregator passed this entry and the quality gate has not judged it. */
    public boolean isUngated() {
        return verdict(CorpusStage.AGGREGATOR).filter(verdict -> verdict == CorpusVerdict.PASS).isPresent()
                && !verdicts.containsKey(CorpusStage.QUALITY);
    }
}
