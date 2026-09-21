package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.nio.file.Path;

/** Durable admission and stage-transition notifications used by generation scheduling. */
public interface WorkflowEvents {
    WorkflowEvents NONE = new WorkflowEvents() {};

    default void admitted(Path path, int generation, boolean mutant) {}

    default void completed(Path path, CorpusStage stage, CorpusVerdict verdict) {}

    default int generationOf(Path path) {
        return 0;
    }
}
