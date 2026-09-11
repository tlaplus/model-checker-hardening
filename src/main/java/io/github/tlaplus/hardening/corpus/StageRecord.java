package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.common.Preconditions;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What a stage records about one input: its verdict, when it worked on the input, and its failure
 * classification when it classifies failures.
 *
 * <p>This is the part shared by {@link StageResult}, which a stage hands to the corpus, and {@link
 * StageMetadata}, which the corpus stores under the stage's name. Its invariants hold for both.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StageRecord(
        CorpusVerdict verdict,
        Instant startTime,
        Instant endTime,
        Optional<CheckerFailure> failure) {
    public StageRecord {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(failure, "failure");
        Preconditions.require(!endTime.isBefore(startTime), "endTime must not precede startTime");
        Preconditions.require(failure.isEmpty() || verdict == CorpusVerdict.FAIL,
                "checker failure metadata requires the fail verdict");
    }

    /** A record of a stage that does not classify failures. */
    public StageRecord(CorpusVerdict verdict, Instant startTime, Instant endTime) {
        this(verdict, startTime, endTime, Optional.empty());
    }
}
