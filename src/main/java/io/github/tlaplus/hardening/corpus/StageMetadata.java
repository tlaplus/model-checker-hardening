package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata recorded when one workflow stage finishes processing an input.
 *
 * <p>The stage is a name rather than a {@link CorpusStage}: a document may record a stage this
 * build has never heard of, and reading an entry must not depend on knowing every stage that ever
 * wrote to it. The verdict is not open in the same way -- a stage records one of the outcomes the
 * corpus stores entries by, so an unknown verdict is a malformed entry rather than an extension.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StageMetadata(
        String stage,
        CorpusVerdict verdict,
        Instant startTime,
        Instant endTime,
        Optional<CheckerFailure> failure) {
    public StageMetadata {
        if (Objects.requireNonNull(stage, "stage").isBlank()) {
            throw new IllegalArgumentException("stage must not be blank");
        }
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(failure, "failure");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not precede startTime");
        }
        if (failure.isPresent() && verdict != CorpusVerdict.FAIL) {
            throw new IllegalArgumentException(
                    "checker failure metadata requires the fail verdict");
        }
        CorpusStage.fromMetadataName(stage)
                .ifPresent(layout -> validateFailureMetadata(
                        layout, verdict, failure.isPresent()));
    }

    public StageMetadata(String stage, CorpusVerdict verdict, Instant startTime, Instant endTime) {
        this(stage, verdict, startTime, endTime, Optional.empty());
    }

    private static void validateFailureMetadata(
            CorpusStage stageLayout, CorpusVerdict verdict, boolean hasFailure) {
        switch (stageLayout) {
            case PARSER -> {
                if (hasFailure) {
                    throw new IllegalArgumentException(
                            "parser metadata must not contain a failure code");
                }
            }
            case TLC, APALACHE -> {
                if (verdict == CorpusVerdict.FAIL && !hasFailure) {
                    throw new IllegalArgumentException(
                            stageLayout.displayName()
                                    + " failure metadata requires a failure code");
                }
            }
        }
    }
}
