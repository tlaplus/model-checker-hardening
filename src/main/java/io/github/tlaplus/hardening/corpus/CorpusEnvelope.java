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

    /**
     * Reports whether a stage recorded a verdict after which no stage of any run reads this
     * entry's input again (ADR 0031 §1).
     */
    public boolean isRetired() {
        return stages.stream().anyMatch(metadata -> CorpusStage.fromMetadataName(metadata.stage())
                .filter(stage -> stage.role().retires(metadata.verdict()))
                .isPresent());
    }
}
