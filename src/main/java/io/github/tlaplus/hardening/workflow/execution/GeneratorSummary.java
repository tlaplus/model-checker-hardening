package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.common.Preconditions;
import java.time.Duration;
import java.util.Objects;

/** Cumulative generation statistics decorated with invocation-facing replay and timing fields. */
public record GeneratorSummary(long seed, long generated, GeneratorAggregate aggregate, Duration elapsed) {
    public GeneratorSummary {
        Preconditions.requireNonnegative(seed, "seed");
        Preconditions.requireNonnegative(generated, "generated");
        Objects.requireNonNull(aggregate, "aggregate");
        Objects.requireNonNull(elapsed, "elapsed");
        Preconditions.require(!elapsed.isNegative(), "generator elapsed time must be nonnegative");
    }
}
