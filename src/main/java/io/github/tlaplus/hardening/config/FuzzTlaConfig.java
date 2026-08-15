package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.Objects;

/** Complete configuration of input generation and workflow execution. */
public record FuzzTlaConfig(
        IrGenerationConfig generator, WorkflowConfig workflow, PbtConfig pbt) {
    public FuzzTlaConfig {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(workflow, "workflow");
        Objects.requireNonNull(pbt, "pbt");
        if (!pbt.supportsDistinctInputs(workflow.maximumEntries())) {
            throw new IllegalArgumentException(
                    "workflow.maximumEntries exceeds the number of distinct bounded inputs");
        }
    }

    /** Returns the configuration written by {@code fuzztla init}. */
    public static FuzzTlaConfig defaults() {
        return new FuzzTlaConfig(
                IrGenerationConfig.defaults(), WorkflowConfig.defaults(), PbtConfig.defaults());
    }
}
