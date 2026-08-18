package io.github.tlaplus.hardening.corpus;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable startup inventory used to seed stage queues and capacities.
 *
 * <p>Every count and pending queue is keyed by {@link CorpusStage}, so a new stage needs no new
 * field here. A stage's pending entries are the contents of its input directory; its counts are the
 * verdicts it has already recorded.
 */
public record CorpusInventory(Map<CorpusStage, StageEntries> stages) {
    /** What one stage holds: the inputs still waiting for it, and the verdicts it has recorded. */
    public record StageEntries(List<Path> pending, StageEntryCounts counts) {
        public StageEntries {
            pending = List.copyOf(pending);
            Objects.requireNonNull(counts, "counts");
        }
    }

    public CorpusInventory {
        Objects.requireNonNull(stages, "stages");
        var copy = new EnumMap<CorpusStage, StageEntries>(CorpusStage.class);
        copy.putAll(stages);
        for (var stage : CorpusStage.values()) {
            if (!copy.containsKey(stage)) {
                throw new IllegalArgumentException("inventory is missing stage " + stage);
            }
        }
        stages = Map.copyOf(copy);

        // A parser pass exists once per checker branch, so each branch accounts for all of them.
        var parserPasses = stages.get(CorpusStage.PARSER).counts().passed();
        for (var checker : CorpusStage.checkerBranches()) {
            var branch = stages.get(checker);
            if (parserPasses != branch.pending().size() + branch.counts().processed()) {
                throw new IllegalArgumentException(
                        "each parser pass must have one entry in each checker branch");
            }
        }
    }

    /** Returns the entries still waiting in one stage's input directory. */
    public List<Path> pending(CorpusStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage")).pending();
    }

    /** Returns the verdicts one stage has recorded. */
    public StageEntryCounts counts(CorpusStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage")).counts();
    }

    /** Returns how many entries wait in one stage's input directory. */
    public long pendingEntries(CorpusStage stage) {
        return pending(stage).size();
    }

    /** Returns how many entries one stage has produced a verdict for. */
    public long processedEntries(CorpusStage stage) {
        return counts(stage).processed();
    }

    /**
     * Returns the current occupancy of one stage's result directories. A stage whose passes fan out
     * downstream keeps only its failures and crashes.
     */
    public long resultEntries(CorpusStage stage) {
        var counts = counts(stage);
        return stage.retainsPasses() ? counts.processed() : counts.failed() + counts.crashed();
    }

    /** Counts one logical input once despite the two physical checker-branch copies. */
    public long totalEntries() {
        return pendingEntries(CorpusStage.PARSER) + processedEntries(CorpusStage.PARSER);
    }
}
