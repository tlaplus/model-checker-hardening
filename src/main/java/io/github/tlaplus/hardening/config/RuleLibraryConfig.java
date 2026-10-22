package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A rule module and its weights (ADR 0017 §1), as {@code [metamorphic]} configures the rewrites of
 * {@code --how=mt} and {@code [graph]} the tautological edge guards of ADR 0023 §6.
 *
 * @param module the rule module, absent when none is configured
 * @param weights rule weights by rule name, overriding a rule's {@code @weight} annotation
 */
@ArchRole(Role.CONFIG)
public record RuleLibraryConfig(Optional<RuleModule> module, Map<String, Integer> weights) {
    /** A rule module and the directories or archives its sources are read from. */
    public record RuleModule(String module, List<Path> classpath) {
        public RuleModule {
            Objects.requireNonNull(module, "module");
            classpath = List.copyOf(Objects.requireNonNull(classpath, "classpath"));
        }

        RuleModule relativeTo(Path directory) {
            return new RuleModule(module, ConfigPaths.relativeTo(classpath, directory));
        }
    }

    public RuleLibraryConfig {
        Objects.requireNonNull(module, "module");
        weights = Map.copyOf(new TreeMap<>(Objects.requireNonNull(weights, "weights")));
    }

    /** No rule module, so no rules. */
    public static RuleLibraryConfig none() {
        return new RuleLibraryConfig(Optional.empty(), Map.of());
    }

    /** The rules of {@code module}, at their annotated weights. */
    public static RuleLibraryConfig of(RuleModule module) {
        return new RuleLibraryConfig(Optional.of(module), Map.of());
    }

    public boolean isEmpty() {
        return module.isEmpty();
    }

    /** Resolves the classpath relative to the directory of the configuration file. */
    RuleLibraryConfig relativeTo(Path directory) {
        return new RuleLibraryConfig(module.map(rules -> rules.relativeTo(directory)), weights);
    }
}
