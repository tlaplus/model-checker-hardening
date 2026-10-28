package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.rewrite.RewriteLimits;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code [metamorphic]} table (ADR 0016 §7, ADR 0017 §1): what an {@code mt} corpus reads
 * besides the generator settings. Another technique ignores it.
 *
 * @param rules the rule module and its weights; an omitted rule has weight 1
 * @param limits the rewrites of one body and the rewrites stacked on one node
 * @param adoption the pbt corpus whose {@code 04quality-pass} entries are adopted, and their share
 */
public record MetamorphicConfig(RuleLibraryConfig rules, RewriteLimits limits, Adoption adoption) {
    /**
     * Where adopted parents come from (ADR 0016 §6).
     *
     * @param baseCorpus the pbt corpus, absent when nothing is adopted
     * @param ratio the share of a generation admitted from adopted parents
     */
    public record Adoption(Optional<Path> baseCorpus, double ratio) {
        public Adoption {
            Objects.requireNonNull(baseCorpus, "baseCorpus");
            if (!(ratio >= 0.0 && ratio <= 1.0)) {
                throw new IllegalArgumentException("adoptRatio must be in the range [0, 1]");
            }
        }

        public static Adoption defaults() {
            return new Adoption(Optional.empty(), 0.5);
        }

        Adoption relativeTo(Path directory) {
            return new Adoption(baseCorpus.map(path -> directory.resolve(path).normalize()), ratio);
        }
    }

    public MetamorphicConfig {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(adoption, "adoption");
    }

    public static MetamorphicConfig defaults() {
        return new MetamorphicConfig(RuleLibraryConfig.none(), RewriteLimits.defaults(), Adoption.defaults());
    }

    /** Resolves the rule classpath relative to the directory of the configuration file. */
    MetamorphicConfig relativeTo(Path directory) {
        return new MetamorphicConfig(rules.relativeTo(directory), limits, adoption.relativeTo(directory));
    }
}
