package io.github.tlaplus.hardening.corpus;

import java.util.List;
import java.util.Objects;

/** Supported fields decoded from one corpus input envelope. */
public record CorpusEnvelope(CorpusInput corpusInput, List<StageMetadata> stages) {
    public CorpusEnvelope {
        Objects.requireNonNull(corpusInput, "corpusInput");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
    }
}
