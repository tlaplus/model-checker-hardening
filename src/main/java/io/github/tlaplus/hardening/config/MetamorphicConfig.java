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
 * @param lifting the pbt corpus whose {@code 04quality-pass} entries are lifted, and their share
 */
public record MetamorphicConfig(
        Optional<RuleModule> rules, Map<String, Integer> weights, RewriteLimits limits, Lifting lifting) {
    /**
     * Where lifted parents come from (ADR 0016 §6).
     *
     * @param baseCorpus the pbt corpus, absent when nothing is lifted
     * @param ratio the share of a generation admitted from lifted parents
     */
    public record Lifting(Optional<Path> baseCorpus, double ratio) {
        public Lifting {
            Objects.requireNonNull(baseCorpus, "baseCorpus");
            if (!(ratio >= 0.0 && ratio <= 1.0)) {
                throw new IllegalArgumentException("liftRatio must be in the range [0, 1]");
            }
        }

        public static Lifting defaults() {
            return new Lifting(Optional.empty(), 0.5);
        }

        Lifting relativeTo(Path directory) {
            return new Lifting(baseCorpus.map(path -> directory.resolve(path).normalize()), ratio);
        }
    }

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
        Objects.requireNonNull(lifting, "lifting");
    }

    public static MetamorphicConfig defaults() {
        return new MetamorphicConfig(Optional.empty(), Map.of(), RewriteLimits.defaults(), Lifting.defaults());
    }

    /** Resolves the rule classpath relative to the directory of the configuration file. */
    MetamorphicConfig relativeTo(Path directory) {
        return new MetamorphicConfig(
                rules.map(module -> module.relativeTo(directory)), weights, limits, lifting.relativeTo(directory));
    }
}
