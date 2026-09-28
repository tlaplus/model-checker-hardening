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
        MutatorConfig mutator,
        OperatorLibraryConfig libraries,
        MetamorphicConfig metamorphic) {
    public FuzzTlaConfig {
        Objects.requireNonNull(metamorphic, "metamorphic");
        Objects.requireNonNull(generatedKind, "generatedKind");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(workflow, "workflow");
        Objects.requireNonNull(pbt, "pbt");
        Objects.requireNonNull(mutator, "mutator");
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

    /** A configuration without metamorphic settings, which only {@code --how=mt} reads. */
    public FuzzTlaConfig(
            InputKind generatedKind,
            IrGenerationConfig generator,
            WorkflowConfig workflow,
            PbtConfig pbt,
            MutatorConfig mutator,
            OperatorLibraryConfig libraries) {
        this(generatedKind, generator, workflow, pbt, mutator, libraries, MetamorphicConfig.defaults());
    }

    /**
     * Returns the default configuration, which consults no known-defect database. {@code fuzztla
     * init} writes it with the repository's shipped database enabled, because only the command
     * knows where the repository is.
     */
    public static FuzzTlaConfig defaults() {
        return new FuzzTlaConfig(
                InputKind.MODULE,
                IrGenerationConfig.defaults(),
                WorkflowConfig.defaults(),
                PbtConfig.defaults(),
                MutatorConfig.defaults(),
                OperatorLibraryConfig.empty());
    }

    /** Returns this configuration with the given custom operator library. */
    public FuzzTlaConfig withLibraries(OperatorLibraryConfig libraries) {
        return new FuzzTlaConfig(generatedKind, generator, workflow, pbt, mutator, libraries, metamorphic);
    }

    /** Returns this configuration with the given workflow limits and checkers. */
    public FuzzTlaConfig withWorkflow(WorkflowConfig replacement) {
        return new FuzzTlaConfig(generatedKind, generator, replacement, pbt, mutator, libraries, metamorphic);
    }

    /** Returns this configuration with the given metamorphic settings. */
    public FuzzTlaConfig withMetamorphic(MetamorphicConfig replacement) {
        return new FuzzTlaConfig(generatedKind, generator, workflow, pbt, mutator, libraries, replacement);
    }

    /** Returns this configuration with the input stage consulting the given known-defect databases. */
    public FuzzTlaConfig withKnownDefects(List<Path> databases) {
        return withWorkflow(workflow.withInputs(workflow.inputs().withKnownDefects(databases)));
    }
}
