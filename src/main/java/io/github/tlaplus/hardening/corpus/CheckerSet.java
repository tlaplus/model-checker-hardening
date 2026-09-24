package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The checker branches a corpus runs: a non-empty set of checking stages, in stage order. The
 * parser fans every pass out to exactly these branches, and the aggregator joins exactly their
 * verdicts (ADR 0016 §5).
 */
public record CheckerSet(List<CorpusStage> stages) implements Iterable<CorpusStage> {
    /** Every checker stage, which is what every corpus ran before ADR 0016. */
    public static final CheckerSet ALL = new CheckerSet(CorpusStage.checkerBranches());

    public CheckerSet {
        Objects.requireNonNull(stages, "stages");
        var ordered = new TreeSet<CorpusStage>(stages);
        Preconditions.require(!ordered.isEmpty(), "a corpus runs at least one checker");
        Preconditions.require(ordered.size() == stages.size(), "a checker is listed twice: " + stages);
        for (var stage : ordered) {
            Preconditions.require(CorpusStage.checkerBranches().contains(stage), stage + " is not a checker stage");
        }
        stages = List.copyOf(ordered);
    }

    public static CheckerSet of(CorpusStage... stages) {
        return new CheckerSet(List.of(stages));
    }

    public boolean contains(CorpusStage stage) {
        return stages.contains(stage);
    }

    public int size() {
        return stages.size();
    }

    /** Returns the first checker in stage order, which inventories use as the reference branch. */
    public CorpusStage first() {
        return stages.getFirst();
    }

    @Override
    public Iterator<CorpusStage> iterator() {
        return stages.iterator();
    }
}
