package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What one stage decided about one input: its verdict, when the stage worked on it, its failure
 * classification when the stage classifies failures, and the tool output kept for triage.
 *
 * <p>The diagnostic becomes the {@code .stacktrace} sidecar of a crash verdict and is otherwise
 * unused. A blank diagnostic is allowed; a crash without one is reported as such in the sidecar.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StageResult(
        CorpusVerdict verdict,
        Instant startTime,
        Instant endTime,
        Optional<CheckerFailure> failure,
        String diagnostic) {
    public StageResult {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not precede startTime");
        }
    }

    /**
     * Returns the current instant as the end of a job that started at {@code startTime}, clamped so
     * that a non-monotonic wall clock cannot report an end before its own start.
     */
    public static Instant endedNow(Instant startTime) {
        var now = Instant.now();
        return now.isBefore(Objects.requireNonNull(startTime, "startTime")) ? startTime : now;
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
}
