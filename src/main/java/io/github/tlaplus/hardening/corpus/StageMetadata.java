package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.common.Preconditions;
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
 *
 * <p>Only invariants of the record itself are checked here, so that this build can read a document
 * a later build wrote. Which failure metadata each stage of <em>this</em> pipeline must record is
 * checked by the transition layer through {@link CorpusStage}.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StageMetadata(String stage, StageRecord record) {
    public StageMetadata {
        requireStage(stage);
        Objects.requireNonNull(record, "record");
    }

    public StageMetadata(
            String stage,
            CorpusVerdict verdict,
            Instant startTime,
            Instant endTime,
            Optional<CheckerFailure> failure) {
        this(requireStage(stage), new StageRecord(verdict, startTime, endTime, failure));
    }

    public StageMetadata(String stage, CorpusVerdict verdict, Instant startTime, Instant endTime) {
        this(stage, verdict, startTime, endTime, Optional.empty());
    }

    public CorpusVerdict verdict() {
        return record.verdict();
    }

    public Instant startTime() {
        return record.startTime();
    }

    public Instant endTime() {
        return record.endTime();
    }

    public Optional<CheckerFailure> failure() {
        return record.failure();
    }

    /** Checks the stage name before the record, so a blank name is reported first. */
    private static String requireStage(String stage) {
        Preconditions.require(!Objects.requireNonNull(stage, "stage").isBlank(), "stage must not be blank");
        return stage;
    }
}
