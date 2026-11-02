package io.github.tlaplus.hardening.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.tomlj.TomlTable;

/**
 * Declares and assembles the {@code rules} and {@code weights} keys of a table that names a rule
 * module (ADR 0017 §1), so every such table reads them alike. A table that names two rule modules
 * prefixes the keys of the second, as {@code temporal_rules} and {@code temporal_weights} (ADR
 * 0027 §1).
 */
final class RuleLibrarySchema {
    private final ConfigSchema.Key<Optional<RuleLibraryConfig.RuleModule>> rules;
    private final ConfigSchema.Key<Map<String, Integer>> weights;

    /**
     * @param keyPrefix the prefix of both keys, empty for the table's only or first rule module
     * @param example the rule module a configuration without one shows, commented out
     * @param ruleNoun what the table calls one rule, such as "rewrite rule"
     * @param rulesDocumentation the documentation of the {@code rules} key, which says what the
     *     rules are for
     */
    RuleLibrarySchema(ConfigTableBuilder<RuleLibraryConfig> table, String keyPrefix, RuleLibraryConfig.RuleModule example,
            String ruleNoun, String... rulesDocumentation) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        Objects.requireNonNull(ruleNoun, "ruleNoun");
        rules = table.optional(
                keyPrefix + "rules", ConfigValueType.ruleModule(example), RuleLibraryConfig::module,
                new ConfigSchema.Absent<>(), rulesDocumentation);
        weights = table.optional(
                keyPrefix + "weights", ConfigValueType.RULE_WEIGHTS, RuleLibraryConfig::weights,
                new ConfigSchema.Constant<>(Map.of()),
                "Selection slots per " + ruleNoun + ", overriding the rule's @weight annotation; a rule",
                "with neither has weight 1, and 0 disables it.");
    }

    /** Reads the table's rule library, with the classpath still relative to the config file. */
    RuleLibraryConfig read(Map<String, TomlTable> tables) throws ConfigException {
        return new RuleLibraryConfig(rules.read(tables), weights.read(tables));
    }
}
