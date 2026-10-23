package io.github.tlaplus.hardening.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.tomlj.TomlTable;

/**
 * Declares and assembles the {@code rules} and {@code weights} keys of a table that names a rule
 * module (ADR 0017 §1), so every such table reads them alike.
 */
final class RuleLibrarySchema {
    private final ConfigSchema.Key<Optional<RuleLibraryConfig.RuleModule>> rules;
    private final ConfigSchema.Key<Map<String, Integer>> weights;

    /**
     * @param ruleNoun what the table calls one rule, such as "rewrite rule"
     * @param rulesDocumentation the documentation of the {@code rules} key, which says what the
     *     rules are for
     */
    RuleLibrarySchema(ConfigTableBuilder<RuleLibraryConfig> table, String ruleNoun, String... rulesDocumentation) {
        Objects.requireNonNull(ruleNoun, "ruleNoun");
        rules = table.optional(
                "rules", ConfigValueType.RULE_MODULE, RuleLibraryConfig::module, new ConfigSchema.Absent<>(),
                rulesDocumentation);
        weights = table.optional(
                "weights", ConfigValueType.RULE_WEIGHTS, RuleLibraryConfig::weights,
                new ConfigSchema.Constant<>(Map.of()),
                "Selection slots per " + ruleNoun + ", overriding the rule's @weight annotation; a rule",
                "with neither has weight 1, and 0 disables it.");
    }

    /** Reads the table's rule library, with the classpath still relative to the config file. */
    RuleLibraryConfig read(Map<String, TomlTable> tables) throws ConfigException {
        return new RuleLibraryConfig(rules.read(tables), weights.read(tables));
    }
}
