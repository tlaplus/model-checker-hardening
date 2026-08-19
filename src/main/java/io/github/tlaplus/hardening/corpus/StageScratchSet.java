package io.github.tlaplus.hardening.corpus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The transient scratch directories of every stage, created and removed together.
 *
 * <p>Closing removes all of them, and reports the first failure with the rest attached, so one
 * failing stage cannot leave the others behind.
 */
public final class StageScratchSet implements AutoCloseable {
    private final Map<CorpusStage, StageScratch> scratches;

    private StageScratchSet(Map<CorpusStage, StageScratch> scratches) {
        this.scratches = scratches;
    }

    static StageScratchSet create(CorpusDirectory corpus) throws IOException, CorpusException {
        var scratches = new EnumMap<CorpusStage, StageScratch>(CorpusStage.class);
        var set = new StageScratchSet(scratches);
        try {
            for (var stage : CorpusStage.values()) {
                scratches.put(stage, corpus.createScratch(stage));
            }
            return set;
        } catch (IOException | CorpusException | RuntimeException failure) {
            try {
                set.close();
            } catch (IOException | RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** Returns the directory this invocation may use for one stage's transient files. */
    public Path directory(CorpusStage stage) {
        return scratches.get(Objects.requireNonNull(stage, "stage")).directory();
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (var scratch : scratches.values()) {
            try {
                scratch.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
