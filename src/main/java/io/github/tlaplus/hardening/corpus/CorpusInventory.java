package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.EnumMaps;
import io.github.tlaplus.hardening.common.Preconditions;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable startup inventory used to seed stage queues and capacities.
 *
 * <p>Every count and pending queue is keyed by {@link CorpusStage}, so a new stage needs no new
 * field here. Pending entries are durable inputs for ordinary stages and reconstructed ready pairs
 * for the input-directory-free aggregator. Counts are verdicts the stage has already recorded.
 */
public record CorpusInventory(Map<CorpusStage, StageEntries> stages) {
    /** What one stage holds: the inputs still waiting for it, and the verdicts it has recorded. */
    public record StageEntries(
            List<Path> pending, StageEntryCounts counts, long resultOccupancy) {
        public StageEntries {
            pending = List.copyOf(pending);
            Objects.requireNonNull(counts, "counts");
            Preconditions.requireNonnegative(resultOccupancy, "resultOccupancy");
        }
    }

    public CorpusInventory {
        stages = EnumMaps.requireAllKeys(CorpusStage.class, stages, "inventory");
        for (var stage : CorpusStage.values()) {
            var supported = stage.resultVerdicts();
            for (var verdict : CorpusVerdict.values()) {
                Preconditions.require(supported.contains(verdict)
                                || stages.get(stage).counts().count(verdict) == 0,
                        stage + " inventory counts unsupported " + verdict.encodedName()
                                + " verdicts");
            }
        }

        // A parser pass exists once per checker branch, so each branch accounts for all of them.
        var parserPasses = stages.get(CorpusStage.PARSER)
                .counts()
                .count(CorpusVerdict.PASS);
        for (var checker : CorpusStage.checkerBranches()) {
            var branch = stages.get(checker);
            Preconditions.require(parserPasses == branch.pending().size() + branch.counts().processed(),
                    "each parser pass must have one entry in each checker branch");
        }
    }

    /** Returns the durable work still waiting for one stage. */
    public List<Path> pending(CorpusStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage")).pending();
    }

    /** Returns the verdicts one stage has recorded. */
    public StageEntryCounts counts(CorpusStage stage) {
        return stages.get(Objects.requireNonNull(stage, "stage")).counts();
    }

    /** Returns how many durable work items wait for one stage. */
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
        return stages.get(Objects.requireNonNull(stage, "stage")).resultOccupancy();
    }

    /** Counts one logical input once despite the two physical checker-branch copies. */
    public long totalEntries() {
        return pendingEntries(CorpusStage.PARSER) + processedEntries(CorpusStage.PARSER);
    }
}
