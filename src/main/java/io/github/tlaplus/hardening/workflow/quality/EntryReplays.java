package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.InputAnalysis;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What one run of the quality gate learns by replaying its entries: their coverage features (ADR
 * 0013) and the number of distinct temporal shapes of their properties (ADR 0028). Each entry is
 * replayed at most once, and only when the archive or the ranking reads the result. An input that
 * fails to replay has no features and no shapes.
 */
final class EntryReplays {
    private record Replay(Set<CoverageFeature> features, long temporalShapes) {
        static final Replay NONE = new Replay(Set.of(), 0);
    }

    private final InputAnalysis analysis;
    private final boolean coverage;
    private final boolean shapes;
    private final Map<String, Replay> replays = new HashMap<>();

    /**
     * @param coverage whether the archive reads coverage features
     * @param shapes whether the ranking reads the temporal shapes
     */
    EntryReplays(InputAnalysis analysis, boolean coverage, boolean shapes) {
        this.analysis = Objects.requireNonNull(analysis, "analysis");
        this.coverage = coverage;
        this.shapes = shapes;
    }

    /** Returns the coverage features of an entry, or none when the archive does not use them. */
    Set<CoverageFeature> features(String digest, CorpusInput input) {
        return coverage ? replay(digest, input).features() : Set.of();
    }

    /** Returns the distinct temporal shapes of an entry, or 0 when the ranking does not read them. */
    long temporalShapes(String digest, CorpusInput input) {
        return shapes ? replay(digest, input).temporalShapes() : 0;
    }

    private Replay replay(String digest, CorpusInput input) {
        var known = replays.get(digest);
        if (known != null) {
            return known;
        }
        Replay replay;
        try {
            var replayed = analysis.analyze(input);
            replay = new Replay(CoverageFeature.of(replayed), replayed.temporalShapes().size());
        } catch (RuntimeException | StackOverflowError failure) {
            replay = Replay.NONE;
        }
        replays.put(digest, replay);
        return replay;
    }
}
