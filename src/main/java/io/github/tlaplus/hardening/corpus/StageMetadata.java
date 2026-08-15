package io.github.tlaplus.hardening.corpus;

import java.time.Instant;
import java.util.Objects;

/** Metadata recorded when one workflow stage finishes processing an input. */
public record StageMetadata(
        String stage, String verdict, Instant startTime, Instant endTime) {
    public StageMetadata {
        if (Objects.requireNonNull(stage, "stage").isBlank()) {
            throw new IllegalArgumentException("stage must not be blank");
        }
        if (Objects.requireNonNull(verdict, "verdict").isBlank()) {
            throw new IllegalArgumentException("verdict must not be blank");
        }
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not precede startTime");
        }
    }
}
