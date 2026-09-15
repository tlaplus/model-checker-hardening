package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.common.Preconditions;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What a stage records about one input: its verdict, when it worked on the input, its failure
 * classification when it classifies failures, and what it explored when it measures exploration.
 *
 * <p>This is the part shared by {@link StageResult}, which a stage hands to the corpus, and {@link
 * StageMetadata}, which the corpus stores under the stage's name. Its invariants hold for both.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StageRecord(
        CorpusVerdict verdict,
        Instant startTime,
        Instant endTime,
        Optional<CheckerFailure> failure,
        Optional<ExplorationMetrics> metrics) {
    public StageRecord {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(metrics, "metrics");
        Preconditions.require(!endTime.isBefore(startTime), "endTime must not precede startTime");
        Preconditions.require(failure.isEmpty() || verdict == CorpusVerdict.FAIL,
                "checker failure metadata requires the fail verdict");
        Preconditions.require(metrics.isEmpty() || verdict != CorpusVerdict.CRASH,
                "a crashed stage records no exploration metrics");
        Preconditions.require(
                verdict == CorpusVerdict.COUNTEREXAMPLE
                        || metrics.stream().noneMatch(
                                value -> value.counts().containsKey(ExplorationCount.TRACE_LENGTH)),
                "a trace length requires the counterexample verdict");
    }

    /** A record of a stage that measures no exploration. */
    public StageRecord(
            CorpusVerdict verdict,
            Instant startTime,
            Instant endTime,
            Optional<CheckerFailure> failure) {
        this(verdict, startTime, endTime, failure, Optional.empty());
    }

    /** A record of a stage that does not classify failures. */
    public StageRecord(CorpusVerdict verdict, Instant startTime, Instant endTime) {
        this(verdict, startTime, endTime, Optional.empty());
    }
}
