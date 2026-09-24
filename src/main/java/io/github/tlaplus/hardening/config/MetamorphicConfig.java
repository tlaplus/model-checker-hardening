package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.rewrite.RewriteLimits;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The {@code [metamorphic]} table (ADR 0016 §7, ADR 0017 §1): what a {@code --how=mt} run reads
 * besides the generator settings. Another technique ignores it.
 *
 * @param rules the rule module, absent when none is configured
 * @param weights rule weights by rule name; an omitted rule has weight 1
 * @param limits the rewrites of one body and the rewrites stacked on one node
 */
public record MetamorphicConfig(Optional<RuleModule> rules, Map<String, Integer> weights, RewriteLimits limits) {
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

    public MetamorphicConfig {
        Objects.requireNonNull(rules, "rules");
        weights = Map.copyOf(new TreeMap<>(Objects.requireNonNull(weights, "weights")));
        Objects.requireNonNull(limits, "limits");
    }

    public static MetamorphicConfig defaults() {
        return new MetamorphicConfig(Optional.empty(), Map.of(), RewriteLimits.defaults());
    }

    /** Resolves the rule classpath relative to the directory of the configuration file. */
    MetamorphicConfig relativeTo(Path directory) {
        return new MetamorphicConfig(rules.map(module -> module.relativeTo(directory)), weights, limits);
    }
}
