package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What one stage decided about one input, and the tool output kept for triage.
 *
 * <p>The diagnostic becomes the {@code .stacktrace} sidecar of a crash verdict and is otherwise
 * unused. A blank diagnostic is allowed; a crash without one is reported as such in the sidecar.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StageResult(StageRecord record, String diagnostic) {
    public StageResult {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(diagnostic, "diagnostic");
    }

    public StageResult(
            CorpusVerdict verdict,
            Instant startTime,
            Instant endTime,
            Optional<CheckerFailure> failure,
            String diagnostic) {
        this(new StageRecord(verdict, startTime, endTime, failure), diagnostic);
    }

    /** A result of a stage that does not classify failures and produced no diagnostic. */
    public StageResult(CorpusVerdict verdict, Instant startTime, Instant endTime) {
        this(verdict, startTime, endTime, Optional.empty(), "");
    }

    /** A result of a stage that does not classify failures. */
    public StageResult(
            CorpusVerdict verdict, Instant startTime, Instant endTime, String diagnostic) {
        this(verdict, startTime, endTime, Optional.empty(), diagnostic);
    }

    /**
     * Returns the current instant as the end of a job that started at {@code startTime}, clamped so
     * that a non-monotonic wall clock cannot report an end before its own start.
     */
    public static Instant endedNow(Instant startTime) {
        var now = Instant.now();
        return now.isBefore(Objects.requireNonNull(startTime, "startTime")) ? startTime : now;
    }

    public CorpusVerdict verdict() {
        return record.verdict();
    }

    public Optional<CheckerFailure> failure() {
        return record.failure();
    }

    /** Returns what the corpus stores for this result under {@code stage}. */
    StageMetadata metadata(CorpusStage stage) {
        return new StageMetadata(stage.metadataName(), record);
    }
}
