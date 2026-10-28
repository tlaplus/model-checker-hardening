package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.corpus.Technique;
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
 *
 * @param graphGuards the rule module of the tautological edge guards of graph inputs (ADR 0023
 *     §6, §7), read from {@code [graph]}; the bounds of a graph are {@link IrGenerationConfig#graphs()}
 */
@ArchRole(Role.CONFIG)
@Key
public record FuzzTlaConfig(
        InputKind generatedKind,
        IrGenerationConfig generator,
        WorkflowConfig workflow,
        PbtConfig pbt,
        MutatorConfig mutator,
        OperatorLibraryConfig libraries,
        MetamorphicConfig metamorphic,
        RuleLibraryConfig graphGuards) {
    public FuzzTlaConfig {
        Objects.requireNonNull(metamorphic, "metamorphic");
        Objects.requireNonNull(graphGuards, "graphGuards");
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

    /** A configuration without metamorphic settings, which only an {@code mt} corpus reads. */
    public FuzzTlaConfig(
            InputKind generatedKind,
            IrGenerationConfig generator,
            WorkflowConfig workflow,
            PbtConfig pbt,
            MutatorConfig mutator,
            OperatorLibraryConfig libraries) {
        this(generatedKind, generator, workflow, pbt, mutator, libraries, MetamorphicConfig.defaults());
    }

    /** A configuration whose graph inputs have no edge guards. */
    public FuzzTlaConfig(
            InputKind generatedKind,
            IrGenerationConfig generator,
            WorkflowConfig workflow,
            PbtConfig pbt,
            MutatorConfig mutator,
            OperatorLibraryConfig libraries,
            MetamorphicConfig metamorphic) {
        this(generatedKind, generator, workflow, pbt, mutator, libraries, metamorphic, RuleLibraryConfig.none());
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
        return new FuzzTlaConfig(generatedKind, generator, workflow, pbt, mutator, libraries, metamorphic, graphGuards);
    }

    /** Returns this configuration with the given workflow limits and checkers. */
    public FuzzTlaConfig withWorkflow(WorkflowConfig replacement) {
        return new FuzzTlaConfig(generatedKind, generator, replacement, pbt, mutator, libraries, metamorphic, graphGuards);
    }

    /** Returns this configuration generating inputs of {@code kind}. */
    public FuzzTlaConfig withGeneratedKind(InputKind kind) {
        return new FuzzTlaConfig(kind, generator, workflow, pbt, mutator, libraries, metamorphic, graphGuards);
    }

    /** Returns this configuration running the given technique. */
    public FuzzTlaConfig withTechnique(Technique technique) {
        return withWorkflow(workflow.withTechnique(technique));
    }

    /** Returns this configuration with the given metamorphic settings. */
    public FuzzTlaConfig withMetamorphic(MetamorphicConfig replacement) {
        return new FuzzTlaConfig(generatedKind, generator, workflow, pbt, mutator, libraries, replacement, graphGuards);
    }

    /** Returns this configuration with the given rule module for the edge guards of graph inputs. */
    public FuzzTlaConfig withGraphGuards(RuleLibraryConfig replacement) {
        return new FuzzTlaConfig(generatedKind, generator, workflow, pbt, mutator, libraries, metamorphic, replacement);
    }

    /** Returns this configuration with the input stage consulting the given known-defect databases. */
    public FuzzTlaConfig withKnownDefects(List<Path> databases) {
        return withWorkflow(workflow.withInputs(workflow.inputs().withKnownDefects(databases)));
    }
}
