package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.Objects;

/**
 * Complete configuration of input generation and workflow execution.
 *
 * <p>The generated kind selects which decoder a run produces with. It is not a generation limit,
 * so it sits here rather than in {@link IrGenerationConfig}: an entry already in the corpus
 * records its own kind and is regenerated through that decoder whatever this run generates.
 */
public record FuzzTlaConfig(
        InputKind generatedKind,
        IrGenerationConfig generator,
        WorkflowConfig workflow,
        PbtConfig pbt) {
    public FuzzTlaConfig {
        Objects.requireNonNull(generatedKind, "generatedKind");
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
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                WorkflowConfig.defaults(),
                PbtConfig.defaults());
    }
}
