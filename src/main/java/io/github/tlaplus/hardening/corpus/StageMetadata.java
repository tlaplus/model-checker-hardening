package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Metadata recorded when one workflow stage finishes processing an input. */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StageMetadata(
        String stage,
        String verdict,
        Instant startTime,
        Instant endTime,
        Optional<CheckerFailure> failure) {
    public StageMetadata {
        if (Objects.requireNonNull(stage, "stage").isBlank()) {
            throw new IllegalArgumentException("stage must not be blank");
        }
        if (Objects.requireNonNull(verdict, "verdict").isBlank()) {
            throw new IllegalArgumentException("verdict must not be blank");
        }
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(failure, "failure");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not precede startTime");
        }
        if (failure.isPresent() && !CorpusVerdict.FAIL.encodedName().equals(verdict)) {
            throw new IllegalArgumentException(
                    "checker failure metadata requires the fail verdict");
        }
        if (CorpusStageLayout.PARSER.metadataName().equals(stage) && failure.isPresent()) {
            throw new IllegalArgumentException(
                    "parser metadata must not contain a checker failure code");
        }
        if (CorpusStageLayout.TLC.metadataName().equals(stage)
                && CorpusVerdict.FAIL.encodedName().equals(verdict)
                && failure.isEmpty()) {
            throw new IllegalArgumentException("TLC failure metadata requires a failure code");
        }
    }

    public StageMetadata(String stage, String verdict, Instant startTime, Instant endTime) {
        this(stage, verdict, startTime, endTime, Optional.empty());
    }
}
