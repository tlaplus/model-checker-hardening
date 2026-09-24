package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.ShallowPattern;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.tomlj.TomlTable;

/**
 * The declaration of every key in {@code config.toml}: where it lives, what it means, how its value
 * is read, and how it is written back.
 *
 * <p>A key is declared exactly once, here. Strict key validation, reading, and rendering are all
 * derived from these declarations, so a key cannot be present in one of the three and absent from
 * another. Adding a setting registers one {@link Key} in its table builder.
 *
 * <p>Declaration order is document order: {@link #TABLES} is the order tables are rendered, and a
 * table's registration order is the order its keys are rendered.
 */
final class ConfigSchema {
    /** What an optional key reads when its table omits it. */
    sealed interface Default<T> {
        T read(Map<String, TomlTable> tables) throws ConfigException;

        /** Whether rendering comments out {@code rendered}, which the default already supplies. */
        boolean inherits(T rendered, FuzzTlaConfig config);
    }

    /**
     * Inherits another key's value. Rendering comments the assignment out while the value is the
     * inherited one, so a fresh config states the limit once.
     */
    record Inherited<T>(Key<T> key) implements Default<T> {
        Inherited {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public T read(Map<String, TomlTable> tables) throws ConfigException {
            return key.read(tables);
        }

        @Override
        public boolean inherits(T rendered, FuzzTlaConfig config) {
            return rendered.equals(key.value().apply(config));
        }
    }

    /**
     * Reads a fixed value, so a config written before the key existed keeps its meaning. Rendering
     * still states the value.
     */
    record Constant<T>(T value) implements Default<T> {
        Constant {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public T read(Map<String, TomlTable> tables) {
            return value;
        }

        @Override
        public boolean inherits(T rendered, FuzzTlaConfig config) {
            return false;
        }
    }

    /**
     * One configuration key: the table it belongs to, its name, its type, the documentation
     * rendered above it, and the value it holds in a configuration.
     *
     * <p>A key with a default ({@code absent}) is optional: a table that omits it reads the
     * default instead.
     */
    record Key<T>(
            String tablePath,
            String name,
            ConfigValueType<T> type,
            List<String> documentation,
            Function<FuzzTlaConfig, T> value,
            Default<T> absent) {
        Key {
            Objects.requireNonNull(tablePath, "tablePath");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
            documentation = List.copyOf(Objects.requireNonNull(documentation, "documentation"));
        }

        /** A required key has no default. */
        Key(String tablePath, String name, ConfigValueType<T> type, List<String> documentation,
                Function<FuzzTlaConfig, T> value) {
            this(tablePath, name, type, documentation, value, null);
        }

        /** Returns the full document path, as it appears in diagnostics. */
        String path() {
            return tablePath + "." + name;
        }

        /** Whether a table may omit this key. */
        boolean isOptional() {
            return absent != null;
        }

        /**
         * Reads this key from the parsed tables of a document, keyed by table path, or its default
         * when the table omits it. Key validation has already rejected an omitted required key.
         */
        T read(Map<String, TomlTable> tables) throws ConfigException {
            if (isOptional() && !table(tables).contains(name)) {
                return absent.read(tables);
            }
            return type.reader().read(table(tables), path(), name);
        }

        /** Returns the documentation and assignment lines of this key. */
        List<String> render(FuzzTlaConfig config) {
            var lines = new ArrayList<String>(documentation.size() + 1);
            documentation.forEach(comment -> lines.add("# " + comment));
            var rendered = value.apply(config);
            var assignment = name + " = " + type.format().apply(rendered);
            var inherited = isOptional() && absent.inherits(rendered, config);
            lines.add(inherited ? "# " + assignment : assignment);
            return lines;
        }

        /** Returns the table this key lives in. */
        private TomlTable table(Map<String, TomlTable> tables) {
            var table = tables.get(tablePath);
            if (table == null) {
                throw new IllegalStateException("table " + tablePath + " was not resolved");
            }
            return table;
        }
    }

    /**
     * One table of the document and the keys declared directly in it. An optional table may be
     * omitted, and then reads every key's default, so all of its keys must be optional.
     */
    record Table(String path, List<Key<?>> keys, boolean optional) {
        Table {
            Objects.requireNonNull(path, "path");
            keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
            for (var key : keys) {
                Preconditions.require(key.tablePath().equals(path),
                        "key " + key.path() + " does not belong to table " + path);
                Preconditions.require(!optional || key.isOptional(),
                        "optional table " + path + " declares required key " + key.name());
            }
        }

        /** Returns the name of this table within its parent. */
        String name() {
            var separator = path.lastIndexOf('.');
            return separator < 0 ? path : path.substring(separator + 1);
        }

        /** Returns the path of the enclosing table, empty for a top-level table. */
        String parentPath() {
            var separator = path.lastIndexOf('.');
            return separator < 0 ? "" : path.substring(0, separator);
        }

        /** Returns the table heading and every key, in declaration order. */
        List<String> render(FuzzTlaConfig config) {
            var lines = new ArrayList<String>();
            lines.add("[" + path + "]");
            keys.forEach(key -> lines.addAll(key.render(config)));
            return lines;
        }
    }

    /**
     * The four keys of one checker table. TLC and Apalache take the same settings, so a checker's
     * keys are derived from its {@link CorpusStage} rather than declared per checker.
     */
    record CheckerKeys(
            Key<Integer> maximumEntries,
            Key<Integer> timeoutSeconds,
            Key<Integer> maximumHeapMegabytes,
            Key<Integer> workers) {}

    private static final String WORKFLOW_PATH = "workflow";
    private static final String INHERITS_MAXIMUM_ENTRIES = "Defaults to workflow.max_entries.";

    private static final ConfigTableBuilder<FuzzTlaConfig> GENERATOR =
            new ConfigTableBuilder<>("generator", Function.identity());
    private static final ConfigTableBuilder<IrGenerationConfig> GENERATION =
            GENERATOR.project(FuzzTlaConfig::generator);
    private static final ConfigTableBuilder<WorkflowConfig> WORKFLOW =
            new ConfigTableBuilder<>(WORKFLOW_PATH, FuzzTlaConfig::workflow);
    private static final ConfigTableBuilder<InputStageConfig> INPUTS =
            new ConfigTableBuilder<>(WORKFLOW_PATH + ".inputs", config -> config.workflow().inputs());
    private static final ConfigTableBuilder<ParserStageConfig> PARSER =
            new ConfigTableBuilder<>(stagePath(CorpusStage.PARSER), config -> config.workflow().parser());
    private static final ConfigTableBuilder<PbtConfig> PBT =
            new ConfigTableBuilder<>("pbt", FuzzTlaConfig::pbt);
    private static final ConfigTableBuilder<MutatorConfig> MUTATOR =
            new ConfigTableBuilder<>("mutator", FuzzTlaConfig::mutator);
    private static final ConfigTableBuilder<QualityGateConfig> GATE = MUTATOR.project(MutatorConfig::gate);
    private static final Map<CorpusStage, Table> CHECKER_TABLES = new EnumMap<>(CorpusStage.class);

    static final Key<InputKind> GENERATED_KIND = GENERATOR.key(
            "kind", ConfigValueType.INPUT_KIND, FuzzTlaConfig::generatedKind,
            "What this run generates: \"expr\" for one expression wrapped in a"
                    + " single-state module, or \"module\" for a whole module.",
            "A corpus entry records its own kind, so a run only generates this one.");
    static final GeneratorLimitSchema GENERATOR_LIMITS = new GeneratorLimitSchema(GENERATION);
    static final Key<Set<ExpressionCategory>> IGNORED_CATEGORIES = GENERATION.key(
            "ignore", ConfigValueType.CATEGORIES, IrGenerationConfig::ignoredCategories);
    static final Key<Map<ExpressionKind, Integer>> FORM_WEIGHTS = GENERATION.key(
            "weights", ConfigValueType.WEIGHTS, IrGenerationConfig::formWeights,
            "Selection slots per expression form, for the forms that are not weighted"
                    + " one.",
            "A form with weight N is N times as likely as an unweighted form applicable"
                    + " to the same request.");
    static final Key<List<Path>> CLASSPATH = GENERATOR.key(
            "classpath", ConfigValueType.PATHS, config -> config.libraries().classpath(),
            "Ordered TLA+ source directories or JARs, relative to this config file.");
    static final Key<List<OperatorLibraryConfig.Module>> CUSTOM_OPERATORS = GENERATOR.key(
            "custom_operators", ConfigValueType.MODULES, config -> config.libraries().modules(),
            "Additional operator kinds: { module = \"MyModule\", operators = [\"MyOp\"] }.",
            "Optional link = \"instance\" calls the module through INSTANCE in TLA+ source (ADR 0014).");
    static final Key<Integer> WORKFLOW_MAXIMUM_ENTRIES = WORKFLOW.integer(
            "max_entries", WorkflowConfig::maximumEntries,
            "Maximum number of unique entries across every workflow directory.",
            "A stage table that omits max_entries inherits this value.");
    static final Key<Integer> INPUTS_MAXIMUM_ENTRIES = INPUTS.optionalInteger(
            "max_entries", InputStageConfig::maximumEntries, WORKFLOW_MAXIMUM_ENTRIES,
            "Maximum current occupancy of 00-inputs.", INHERITS_MAXIMUM_ENTRIES);
    static final Key<List<Path>> KNOWN_DEFECTS = INPUTS.key(
            "known_defects", ConfigValueType.PATHS, InputStageConfig::knownDefects,
            "Known-defect signature databases, relative to this config file.",
            "A candidate that matches a signature goes to 00-known-defects; [] admits every"
                    + " candidate.");
    static final Key<Integer> KNOWN_DEFECT_SAMPLES = INPUTS.integer(
            "known_defect_samples", InputStageConfig::knownDefectSamples,
            "Quarantined entries kept per signature in 00-known-defects; further matches are"
                    + " only counted.");
    static final Key<Integer> PARSER_MAXIMUM_ENTRIES = PARSER.optionalInteger(
            "max_entries", ParserStageConfig::maximumEntries, WORKFLOW_MAXIMUM_ENTRIES,
            resultDirectoryDocumentation(CorpusStage.PARSER), INHERITS_MAXIMUM_ENTRIES);
    static final Key<Integer> PARSER_TIMEOUT_SECONDS = PARSER.integer(
            "timeout_sec", ParserStageConfig::timeoutSeconds,
            "Wall-clock limit for parsing one generated specification.");
    private static final Map<CorpusStage, CheckerKeys> CHECKER_KEYS = checkerKeys();
    static final Key<Integer> MAXIMUM_INPUT_BYTES = PBT.integer(
            "max_input_bytes", PbtConfig::maximumInputBytes,
            "Inclusive upper bound on a randomly generated input's length.");
    static final Key<Integer> RICHNESS_COHORTS = PBT.integer(
            "richness_cohorts", PbtConfig::richnessCohorts,
            "Number of uniformly selected collection-richness cohorts.");
    static final Key<Double> RICHNESS_NESTING_BASE = PBT.key(
            "richness_nesting_base", ConfigValueType.NUMBER, PbtConfig::richnessNestingBase,
            "Weight multiplier for each level of collection nesting.");
    static final Key<Double> RICHNESS_THRESHOLD_BASE = PBT.key(
            "richness_threshold_base", ConfigValueType.NUMBER, PbtConfig::richnessThresholdBase,
            "Growth factor for successive cohort admission thresholds.");
    static final Key<Integer> GENERATION_SIZE = MUTATOR.integer(
            "generation_size", MutatorConfig::generationSize,
            "Entries each generation admits before the quality gate selects from it.");
    static final Key<Double> SELECT_FRACTION = GATE.key(
            "select_fraction", ConfigValueType.NUMBER, QualityGateConfig::selectFraction,
            "Upper bound on the share of a generation's agreeing entries the gate keeps in"
                    + " 04quality-pass.");
    static final Key<Double> FEEDBACK_RATIO = MUTATOR.key(
            "feedback_ratio", ConfigValueType.NUMBER, MutatorConfig::feedbackRatio,
            "Share of a generation mutated from 04quality-pass; the rest is generated by PBT.");
    static final Key<Integer> MAXIMUM_EDITS = MUTATOR.integer(
            "max_edits", MutatorConfig::maximumEdits,
            "Cap on the edits stacked on one mutant.");
    static final Key<Map<MutationOperator, Integer>> OPERATOR_WEIGHTS = MUTATOR.key(
            "weights", ConfigValueType.OPERATOR_WEIGHTS, MutatorConfig::weights,
            "Relative weight of each mutation operator; an omitted operator has weight 0.");
    static final Key<Set<ShallowPattern>> SHALLOW_PATTERNS = GATE.key(
            "shallow_patterns", ConfigValueType.SHALLOW_PATTERNS, QualityGateConfig::shallowPatterns,
            "Exploration patterns of ADR 0008 that exclude an entry from 04quality-pass.");
    static final Key<Integer> CELL_CAPACITY = GATE.integer(
            "cell_capacity", QualityGateConfig::cellCapacity,
            "Entries of 04quality-pass per behaviour cell of ADR 0013; 0 removes the bound.");
    static final Key<Boolean> FEATURE_COVERAGE = GATE.key(
            "feature_coverage", ConfigValueType.BOOLEAN, QualityGateConfig::featureCoverage,
            "Also keep an entry that adds an operator-edge coverage feature (ADR 0013).");

    /** Every table of the document, in the order a configuration file declares them. */
    static final List<Table> TABLES = tables();

    private ConfigSchema() {}

    /** Returns the keys of one checker stage. */
    static CheckerKeys checker(CorpusStage stage) {
        var keys = CHECKER_KEYS.get(Objects.requireNonNull(stage, "stage"));
        Preconditions.require(keys != null, stage + " is not a checker stage");
        return keys;
    }

    /**
     * Returns the keys one table may declare directly: the keys declared in it and the names of
     * the tables nested immediately inside it. The empty path denotes the root of the document.
     */
    static Set<String> expectedKeys(String path) {
        return keys(path, key -> true);
    }

    /**
     * Returns the keys one table must declare directly: the keys declared without a default and
     * the names of the required tables nested immediately inside it.
     */
    static Set<String> requiredKeys(String path) {
        return keys(path, key -> !key.isOptional(), table -> !table.optional());
    }

    private static Set<String> keys(String path, Predicate<Key<?>> filter) {
        return keys(path, filter, table -> true);
    }

    private static Set<String> keys(
            String path, Predicate<Key<?>> keyFilter, Predicate<Table> nestedFilter) {
        Objects.requireNonNull(path, "path");
        var expected = new LinkedHashSet<String>();
        for (var table : TABLES) {
            if (table.path().equals(path)) {
                table.keys().stream().filter(keyFilter).forEach(key -> expected.add(key.name()));
            }
            if (table.parentPath().equals(path) && nestedFilter.test(table)) {
                expected.add(table.name());
            }
        }
        return Set.copyOf(expected);
    }

    /** Renders a complete configuration file, including its documentation. */
    static String render(FuzzTlaConfig config) {
        Objects.requireNonNull(config, "config");
        return TABLES.stream()
                .map(table -> String.join("\n", table.render(config)))
                .collect(Collectors.joining("\n\n", "", "\n"));
    }

    private static List<Table> tables() {
        var tables = new ArrayList<>(List.of(GENERATOR.build(), WORKFLOW.build(), INPUTS.build(), PARSER.build()));
        CorpusStage.checkerBranches().forEach(stage -> tables.add(CHECKER_TABLES.get(stage)));
        tables.add(PBT.build());
        tables.add(MUTATOR.build());
        return List.copyOf(tables);
    }

    private static Map<CorpusStage, CheckerKeys> checkerKeys() {
        var keys = new EnumMap<CorpusStage, CheckerKeys>(CorpusStage.class);
        for (var stage : CorpusStage.checkerBranches()) {
            var profile = CheckerProfile.of(stage);
            var defaults = profile.defaults();
            // A checker table may be omitted, so a corpus that does not run the checker (ADR 0016
            // §5) need not configure it; an omitted setting takes the checker's default.
            var table = new ConfigTableBuilder<>(stagePath(stage), config -> config.workflow().checker(stage));
            keys.put(stage, new CheckerKeys(
                    table.optionalInteger("max_entries", CheckerStageConfig::maximumEntries,
                            WORKFLOW_MAXIMUM_ENTRIES,
                            resultDirectoryDocumentation(stage), INHERITS_MAXIMUM_ENTRIES),
                    table.defaultedInteger("timeout_sec", CheckerStageConfig::timeoutSeconds,
                            defaults.timeoutSeconds(),
                            "Wall-clock limit for checking one generated specification."),
                    table.defaultedInteger("max_heap_mb", CheckerStageConfig::maximumHeapMegabytes,
                            defaults.maximumHeapMegabytes(), profile.heapDocumentation()),
                    table.defaultedInteger("workers", CheckerStageConfig::workers, defaults.workers(),
                            profile.workersDocumentation().toArray(String[]::new))));
            CHECKER_TABLES.put(stage, table.buildOptional());
        }
        return Map.copyOf(keys);
    }

    /** Returns the table path of one stage, so a stage is named the same way everywhere. */
    private static String stagePath(CorpusStage stage) {
        return WORKFLOW_PATH + "." + stage.metadataName();
    }

    private static String resultDirectoryDocumentation(CorpusStage stage) {
        return "Maximum combined occupancy of the " + stage.displayName() + " result directories.";
    }
}
