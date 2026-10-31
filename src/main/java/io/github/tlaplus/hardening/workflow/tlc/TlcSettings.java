package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import java.util.Objects;

/**
 * What one TLC stage launches its JVMs with: the stage's limits and the TLC seeds of {@code
 * [seeds]} (ADR 0026), which every TLC stage shares.
 */
public record TlcSettings(CheckerStageConfig stage, SeedConfig seeds) {
    public TlcSettings {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(seeds, "seeds");
    }
}
