package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.engine.CustomExpressionKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        PbtConfig pbt,
        OperatorLibraryConfig libraries) {
    public FuzzTlaConfig {
        Objects.requireNonNull(generatedKind, "generatedKind");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(workflow, "workflow");
        Objects.requireNonNull(pbt, "pbt");
        Objects.requireNonNull(libraries, "libraries");
        var selected = Set.copyOf(libraries.operators());
        for (var kind : generator.formWeights().keySet()) {
            if (kind instanceof CustomExpressionKind custom
                    && !selected.contains(custom.id())) {
                throw new IllegalArgumentException("weight names an unselected custom operator: " + custom.id());
            }
        }
        if (!pbt.supportsDistinctInputs(workflow.maximumEntries())) {
            throw new IllegalArgumentException(
                    "workflow.maximumEntries exceeds the number of distinct bounded inputs");
        }
    }

    /**
     * Returns the default configuration, which consults no known-defect database. {@code fuzztla
     * init} writes it with the repository's shipped database enabled, because only the command
     * knows where the repository is.
     */
    public static FuzzTlaConfig defaults() {
        return new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                WorkflowConfig.defaults(),
                PbtConfig.defaults(), OperatorLibraryConfig.empty());
    }

    /** Returns this configuration with the input stage consulting the given known-defect databases. */
    public FuzzTlaConfig withKnownDefects(List<Path> databases) {
        var inputs = workflow.inputs().withKnownDefects(databases);
        return new FuzzTlaConfig(
                generatedKind,
                generator,
                new WorkflowConfig(workflow.maximumEntries(), inputs, workflow.parser(), workflow.checkers()),
                pbt,
                libraries);
    }
}
