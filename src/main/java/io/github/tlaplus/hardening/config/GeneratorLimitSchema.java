package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.ActionLimits;
import io.github.tlaplus.hardening.gen.ExpressionLimits;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.ModuleLimits;
import java.util.Map;
import org.tomlj.TomlTable;

/** Declares and assembles the expression and module limit keys in the generator table. */
final class GeneratorLimitSchema {
    private final ConfigSchema.Key<Integer> maximumTypeDepth;
    private final ConfigSchema.Key<Integer> maximumExpressionDepth;
    private final ConfigSchema.Key<Integer> maximumNodes;
    private final ConfigSchema.Key<Integer> maximumCollectionSize;
    private final ConfigSchema.Key<Integer> maximumStringBytes;
    private final ConfigSchema.Key<Integer> maximumIntegerBytes;
    private final ConfigSchema.Key<Integer> maximumVariables;
    private final ConfigSchema.Key<Integer> maximumAuxiliaryOperators;
    private final ConfigSchema.Key<Integer> maximumActionOperators;
    private final ConfigSchema.Key<Integer> maximumActions;
    private final ConfigSchema.Key<Integer> maximumActionParameters;
    private final ConfigSchema.Key<Integer> maximumActionDepth;
    private final ConfigSchema.Key<Integer> maximumSteps;

    GeneratorLimitSchema(ConfigTableBuilder<IrGenerationConfig> generator) {
        var expressions = generator.project(IrGenerationConfig::expressions);
        maximumTypeDepth = expressions.integer("max_type_depth", ExpressionLimits::maximumTypeDepth);
        maximumExpressionDepth = expressions.integer("max_expression_depth", ExpressionLimits::maximumExpressionDepth);
        maximumNodes = expressions.integer("max_nodes", ExpressionLimits::maximumNodes);
        maximumCollectionSize = expressions.integer("max_collection_size", ExpressionLimits::maximumCollectionSize);
        maximumStringBytes = expressions.integer("max_string_bytes", ExpressionLimits::maximumStringBytes);
        maximumIntegerBytes = expressions.integer("max_integer_bytes", ExpressionLimits::maximumIntegerBytes);
        var modules = generator.project(IrGenerationConfig::modules);
        maximumVariables = modules.integer("max_variables", ModuleLimits::maximumVariables,
                "Maximum state variables declared by a generated module.");
        maximumAuxiliaryOperators = modules.integer("max_auxiliary_operators", ModuleLimits::maximumAuxiliaryOperators,
                "Maximum state-free operator definitions a generated module may"
                        + " apply.");
        var actions = modules.project(ModuleLimits::actions);
        maximumActionOperators = actions.integer("max_action_operators", ActionLimits::maximumActionOperators,
                "Maximum action operator definitions the next-state action may"
                        + " apply.",
                "Each reads current state and primes a subset of the variables.");
        maximumActions = actions.integer("max_actions", ActionLimits::maximumActions,
                "Maximum disjuncts in a generated next-state action.");
        maximumActionParameters = actions.integer("max_action_parameters", ActionLimits::maximumActionParameters,
                "Maximum bounded existential parameters of one generated action.");
        maximumActionDepth = actions.integer("max_action_depth", ActionLimits::maximumActionDepth,
                "Maximum nesting depth of disjunctions, conjunctions, and"
                        + " IF-THEN-ELSE",
                "within one generated next-state action disjunct. Zero keeps every"
                        + " disjunct a flat conjunction.");
        maximumSteps = modules.integer("max_steps", ModuleLimits::maximumSteps,
                "Transitions explored from an initial state of a generated module.",
                "Bounds Apalache's unrolling and TLC's state constraint alike.");
    }

    ExpressionLimits readExpressionLimits(Map<String, TomlTable> tables)
            throws ConfigException {
        return new ExpressionLimits(
                maximumTypeDepth.read(tables),
                maximumExpressionDepth.read(tables),
                maximumNodes.read(tables),
                maximumCollectionSize.read(tables),
                maximumStringBytes.read(tables),
                maximumIntegerBytes.read(tables));
    }

    ModuleLimits readModuleLimits(Map<String, TomlTable> tables) throws ConfigException {
        return new ModuleLimits(maximumVariables.read(tables), maximumAuxiliaryOperators.read(tables),
                new ActionLimits(maximumActionOperators.read(tables), maximumActions.read(tables),
                        maximumActionParameters.read(tables), maximumActionDepth.read(tables)),
                maximumSteps.read(tables));
    }
}
