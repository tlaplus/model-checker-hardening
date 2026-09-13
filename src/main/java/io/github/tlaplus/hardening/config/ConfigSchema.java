package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    /**
     * One configuration key: the table it belongs to, its name, its type, the documentation
     * rendered above it, and the value it holds in a configuration.
     */
    record Key<T>(
            String tablePath,
            String name,
            ConfigValueType<T> type,
            List<String> documentation,
            Function<FuzzTlaConfig, T> value) {
        Key {
            Objects.requireNonNull(tablePath, "tablePath");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
            documentation = List.copyOf(Objects.requireNonNull(documentation, "documentation"));
        }

        /** Returns the full document path, as it appears in diagnostics. */
        String path() {
            return tablePath + "." + name;
        }

        /** Reads this key from the parsed tables of a document, keyed by table path. */
        T read(Map<String, TomlTable> tables) throws ConfigException {
            var table = tables.get(tablePath);
            if (table == null) {
                throw new IllegalStateException("table " + tablePath + " was not resolved");
            }
            return type.reader().read(table, path(), name);
        }

        /** Returns the documentation and assignment lines of this key. */
        List<String> render(FuzzTlaConfig config) {
            var lines = new ArrayList<String>(documentation.size() + 1);
            documentation.forEach(comment -> lines.add("# " + comment));
            lines.add(name + " = " + type.format().apply(value.apply(config)));
            return lines;
        }
    }

    /** One table of the document and the keys declared directly in it. */
    record Table(String path, List<Key<?>> keys) {
        Table {
            Objects.requireNonNull(path, "path");
            keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
            for (var key : keys) {
                Preconditions.require(key.tablePath().equals(path),
                        "key " + key.path() + " does not belong to table " + path);
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
            "Additional operator kinds: { module = \"MyModule\", operators = [\"MyOp\"] }.");
    static final Key<Integer> WORKFLOW_MAXIMUM_ENTRIES = WORKFLOW.integer(
            "max_entries", WorkflowConfig::maximumEntries,
            "Maximum number of unique entries across every workflow directory.");
    static final Key<Integer> INPUTS_MAXIMUM_ENTRIES = INPUTS.integer(
            "max_entries", InputStageConfig::maximumEntries, "Maximum current occupancy of 00-inputs.");
    static final Key<List<Path>> KNOWN_DEFECTS = INPUTS.key(
            "known_defects", ConfigValueType.PATHS, InputStageConfig::knownDefects,
            "Known-defect signature databases, relative to this config file.",
            "A candidate that matches a signature goes to 00-known-defects; [] admits every"
                    + " candidate.");
    static final Key<Integer> KNOWN_DEFECT_SAMPLES = INPUTS.integer(
            "known_defect_samples", InputStageConfig::knownDefectSamples,
            "Quarantined entries kept per signature in 00-known-defects; further matches are"
                    + " only counted.");
    static final Key<Integer> PARSER_MAXIMUM_ENTRIES = PARSER.integer(
            "max_entries", ParserStageConfig::maximumEntries, resultDirectoryDocumentation(CorpusStage.PARSER));
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
     * Returns the keys required directly in one table: the keys declared in it and the names of the
     * tables nested immediately inside it. The empty path denotes the root of the document.
     */
    static Set<String> expectedKeys(String path) {
        Objects.requireNonNull(path, "path");
        var expected = new LinkedHashSet<String>();
        for (var table : TABLES) {
            if (table.path().equals(path)) {
                table.keys().forEach(key -> expected.add(key.name()));
            }
            if (table.parentPath().equals(path)) {
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
        return List.copyOf(tables);
    }

    private static Map<CorpusStage, CheckerKeys> checkerKeys() {
        var keys = new EnumMap<CorpusStage, CheckerKeys>(CorpusStage.class);
        for (var stage : CorpusStage.checkerBranches()) {
            var profile = CheckerProfile.of(stage);
            var table = new ConfigTableBuilder<>(stagePath(stage), config -> config.workflow().checker(stage));
            keys.put(stage, new CheckerKeys(
                    table.integer("max_entries", CheckerStageConfig::maximumEntries, resultDirectoryDocumentation(stage)),
                    table.integer("timeout_sec", CheckerStageConfig::timeoutSeconds,
                            "Wall-clock limit for checking one generated specification."),
                    table.integer("max_heap_mb", CheckerStageConfig::maximumHeapMegabytes, profile.heapDocumentation()),
                    table.integer("workers", CheckerStageConfig::workers,
                            profile.workersDocumentation().toArray(String[]::new))));
            CHECKER_TABLES.put(stage, table.build());
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
