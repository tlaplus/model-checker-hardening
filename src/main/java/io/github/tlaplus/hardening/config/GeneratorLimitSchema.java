package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.ExpressionLimits;
import io.github.tlaplus.hardening.gen.ModuleLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.tomlj.TomlTable;

/** Declares and assembles the expression and module limit keys in the generator table. */
final class GeneratorLimitSchema {
    private final ExpressionKeys expressions;
    private final ModuleKeys modules;

    private GeneratorLimitSchema(String tablePath) {
        expressions = new ExpressionKeys(
                integerKey(
                        tablePath,
                        "max_type_depth",
                        config -> config.generator().expressions().maximumTypeDepth()),
                integerKey(
                        tablePath,
                        "max_expression_depth",
                        config -> config.generator().expressions().maximumExpressionDepth()),
                integerKey(
                        tablePath,
                        "max_nodes",
                        config -> config.generator().expressions().maximumNodes()),
                integerKey(
                        tablePath,
                        "max_collection_size",
                        config -> config.generator().expressions().maximumCollectionSize()),
                integerKey(
                        tablePath,
                        "max_string_bytes",
                        config -> config.generator().expressions().maximumStringBytes()),
                integerKey(
                        tablePath,
                        "max_integer_bytes",
                        config -> config.generator().expressions().maximumIntegerBytes()));
        modules = new ModuleKeys(
                integerKey(
                        tablePath,
                        "max_variables",
                        List.of("Maximum state variables declared by a generated module."),
                        config -> config.generator().modules().maximumVariables()),
                integerKey(
                        tablePath,
                        "max_auxiliary_operators",
                        List.of("Maximum operator definitions a generated module may apply."),
                        config -> config.generator().modules().maximumAuxiliaryOperators()),
                integerKey(
                        tablePath,
                        "max_actions",
                        List.of("Maximum disjuncts in a generated next-state action."),
                        config -> config.generator().modules().maximumActions()),
                integerKey(
                        tablePath,
                        "max_action_parameters",
                        List.of("Maximum bounded existential parameters of one generated action."),
                        config -> config.generator().modules().maximumActionParameters()),
                integerKey(
                        tablePath,
                        "max_steps",
                        List.of(
                                "Transitions explored from an initial state of a generated module.",
                                "Bounds Apalache's unrolling and TLC's state constraint alike."),
                        config -> config.generator().modules().maximumSteps()));
    }

    static GeneratorLimitSchema in(String tablePath) {
        return new GeneratorLimitSchema(tablePath);
    }

    List<ConfigSchema.Key<?>> keys() {
        var keys = new ArrayList<ConfigSchema.Key<?>>();
        keys.addAll(expressions.keys());
        keys.addAll(modules.keys());
        return List.copyOf(keys);
    }

    ExpressionLimits readExpressionLimits(Map<String, TomlTable> tables)
            throws ConfigException {
        return expressions.read(tables);
    }

    ModuleLimits readModuleLimits(Map<String, TomlTable> tables) throws ConfigException {
        return modules.read(tables);
    }

    private static ConfigSchema.Key<Integer> integerKey(
            String tablePath, String name, Function<FuzzTlaConfig, Integer> value) {
        return integerKey(tablePath, name, List.of(), value);
    }

    private static ConfigSchema.Key<Integer> integerKey(
            String tablePath,
            String name,
            List<String> documentation,
            Function<FuzzTlaConfig, Integer> value) {
        return new ConfigSchema.Key<>(
                tablePath, name, ConfigValueType.INTEGER, documentation, value);
    }

    private record ExpressionKeys(
            ConfigSchema.Key<Integer> maximumTypeDepth,
            ConfigSchema.Key<Integer> maximumExpressionDepth,
            ConfigSchema.Key<Integer> maximumNodes,
            ConfigSchema.Key<Integer> maximumCollectionSize,
            ConfigSchema.Key<Integer> maximumStringBytes,
            ConfigSchema.Key<Integer> maximumIntegerBytes) {
        List<ConfigSchema.Key<?>> keys() {
            return List.of(
                    maximumTypeDepth,
                    maximumExpressionDepth,
                    maximumNodes,
                    maximumCollectionSize,
                    maximumStringBytes,
                    maximumIntegerBytes);
        }

        ExpressionLimits read(Map<String, TomlTable> tables) throws ConfigException {
            return new ExpressionLimits(
                    maximumTypeDepth.read(tables),
                    maximumExpressionDepth.read(tables),
                    maximumNodes.read(tables),
                    maximumCollectionSize.read(tables),
                    maximumStringBytes.read(tables),
                    maximumIntegerBytes.read(tables));
        }
    }

    private record ModuleKeys(
            ConfigSchema.Key<Integer> maximumVariables,
            ConfigSchema.Key<Integer> maximumAuxiliaryOperators,
            ConfigSchema.Key<Integer> maximumActions,
            ConfigSchema.Key<Integer> maximumActionParameters,
            ConfigSchema.Key<Integer> maximumSteps) {
        List<ConfigSchema.Key<?>> keys() {
            return List.of(
                    maximumVariables,
                    maximumAuxiliaryOperators,
                    maximumActions,
                    maximumActionParameters,
                    maximumSteps);
        }

        ModuleLimits read(Map<String, TomlTable> tables) throws ConfigException {
            return new ModuleLimits(
                    maximumVariables.read(tables),
                    maximumAuxiliaryOperators.read(tables),
                    maximumActions.read(tables),
                    maximumActionParameters.read(tables),
                    maximumSteps.read(tables));
        }
    }
}
