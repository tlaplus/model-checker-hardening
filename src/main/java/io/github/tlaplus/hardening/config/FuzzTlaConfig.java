package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.Objects;

/** Complete configuration of the generator and the initial PBT corpus. */
public record FuzzTlaConfig(IrGenerationConfig generator, PbtConfig pbt) {
    public FuzzTlaConfig {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(pbt, "pbt");
    }

    /** Returns the configuration written by {@code fuzztla init}. */
    public static FuzzTlaConfig defaults() {
        return new FuzzTlaConfig(IrGenerationConfig.defaults(), PbtConfig.defaults());
    }
}
