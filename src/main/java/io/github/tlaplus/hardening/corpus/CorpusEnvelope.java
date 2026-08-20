package io.github.tlaplus.hardening.corpus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Supported fields decoded from one corpus input envelope. */
public record CorpusEnvelope(
        CorpusInput corpusInput,
        Optional<GenerationMetadata> generation,
        List<StageMetadata> stages) {
    public CorpusEnvelope {
        Objects.requireNonNull(corpusInput, "corpusInput");
        Objects.requireNonNull(generation, "generation");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
    }

    /** Returns what one stage recorded on this entry, if it has processed it. */
    public Optional<StageMetadata> stage(CorpusStage stage) {
        Objects.requireNonNull(stage, "stage");
        return stages.stream()
                .filter(metadata -> stage.metadataName().equals(metadata.stage()))
                .findFirst();
    }
}
