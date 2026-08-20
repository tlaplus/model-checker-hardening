package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
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
 * another. Adding a setting is one {@link Key} in one {@link Table}.
 *
 * <p>Declaration order is document order: {@link #TABLES} is the order tables are rendered, and a
 * table's key order is the order its keys are rendered.
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
                if (!key.tablePath().equals(path)) {
                    throw new IllegalArgumentException(
                            "key " + key.path() + " does not belong to table " + path);
                }
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
            Key<Integer> workers) {
        List<Key<?>> all() {
            return List.of(maximumEntries, timeoutSeconds, maximumHeapMegabytes, workers);
        }
    }

    /**
     * The checker documentation that genuinely differs between checkers, because their worker
     * lifecycles differ: TLC runs many inputs in one isolated JVM, Apalache one input per worker.
     */
    private record CheckerDocumentation(String heap, List<String> workers) {}

    private static final String GENERATOR_PATH = "generator";
    private static final String WORKFLOW_PATH = "workflow";
    private static final String INPUTS_PATH = WORKFLOW_PATH + ".inputs";
    private static final String PBT_PATH = "pbt";

    private static final String CHECKER_TIMEOUT_DOCUMENTATION =
            "Wall-clock limit for checking one generated specification.";

    private static final Map<CorpusStage, CheckerDocumentation> CHECKER_DOCUMENTATION = Map.of(
            CorpusStage.TLC,
            new CheckerDocumentation(
                    "Maximum heap allocated to each isolated TLC JVM.",
                    List.of("Number of TLC model-checking workers in each isolated JVM.")),
            CorpusStage.APALACHE,
            new CheckerDocumentation(
                    "Maximum heap allocated to each persistent Apalache worker JVM.",
                    List.of(
                            "Number of concurrent FuzzTLA Apalache workers.",
                            "Initialized to half the available processors, rounded down"
                                    + " (at least one).")));

    static final Key<Integer> MAXIMUM_TYPE_DEPTH = generatorKey(
            "max_type_depth", config -> config.generator().maximumTypeDepth());
    static final Key<Integer> MAXIMUM_EXPRESSION_DEPTH = generatorKey(
            "max_expression_depth", config -> config.generator().maximumExpressionDepth());
    static final Key<Integer> MAXIMUM_NODES =
            generatorKey("max_nodes", config -> config.generator().maximumNodes());
    static final Key<Integer> MAXIMUM_COLLECTION_SIZE = generatorKey(
            "max_collection_size", config -> config.generator().maximumCollectionSize());
    static final Key<Integer> MAXIMUM_STRING_BYTES = generatorKey(
            "max_string_bytes", config -> config.generator().maximumStringBytes());
    static final Key<Integer> MAXIMUM_INTEGER_BYTES = generatorKey(
            "max_integer_bytes", config -> config.generator().maximumIntegerBytes());
    static final Key<Set<ExpressionCategory>> IGNORED_CATEGORIES = new Key<>(
            GENERATOR_PATH,
            "ignore",
            ConfigValueType.CATEGORIES,
            List.of(),
            config -> config.generator().ignoredCategories());

    static final Key<Integer> WORKFLOW_MAXIMUM_ENTRIES = new Key<>(
            WORKFLOW_PATH,
            "max_entries",
            ConfigValueType.INTEGER,
            List.of("Maximum number of unique entries across every workflow directory."),
            config -> config.workflow().maximumEntries());

    static final Key<Integer> INPUTS_MAXIMUM_ENTRIES = new Key<>(
            INPUTS_PATH,
            "max_entries",
            ConfigValueType.INTEGER,
            List.of("Maximum current occupancy of 00-inputs."),
            config -> config.workflow().inputs().maximumEntries());

    static final Key<Integer> PARSER_MAXIMUM_ENTRIES = new Key<>(
            stagePath(CorpusStage.PARSER),
            "max_entries",
            ConfigValueType.INTEGER,
            List.of(resultDirectoryDocumentation(CorpusStage.PARSER)),
            config -> config.workflow().parser().maximumEntries());
    static final Key<Integer> PARSER_TIMEOUT_SECONDS = new Key<>(
            stagePath(CorpusStage.PARSER),
            "timeout_sec",
            ConfigValueType.INTEGER,
            List.of("Wall-clock limit for parsing one generated specification."),
            config -> config.workflow().parser().timeoutSeconds());

    private static final Map<CorpusStage, CheckerKeys> CHECKER_KEYS = checkerKeys();

    static final Key<Integer> MAXIMUM_INPUT_BYTES = new Key<>(
            PBT_PATH,
            "max_input_bytes",
            ConfigValueType.INTEGER,
            List.of("Inclusive upper bound on a randomly generated input's length."),
            config -> config.pbt().maximumInputBytes());
    static final Key<Integer> RICHNESS_COHORTS = new Key<>(
            PBT_PATH,
            "richness_cohorts",
            ConfigValueType.INTEGER,
            List.of("Number of uniformly selected collection-richness cohorts."),
            config -> config.pbt().richnessCohorts());
    static final Key<Double> RICHNESS_NESTING_BASE = new Key<>(
            PBT_PATH,
            "richness_nesting_base",
            ConfigValueType.NUMBER,
            List.of("Weight multiplier for each level of collection nesting."),
            config -> config.pbt().richnessNestingBase());
    static final Key<Double> RICHNESS_THRESHOLD_BASE = new Key<>(
            PBT_PATH,
            "richness_threshold_base",
            ConfigValueType.NUMBER,
            List.of("Growth factor for successive cohort admission thresholds."),
            config -> config.pbt().richnessThresholdBase());

    /** Every table of the document, in the order a configuration file declares them. */
    static final List<Table> TABLES = tables();

    private ConfigSchema() {}

    /** Returns the keys of one checker stage. */
    static CheckerKeys checker(CorpusStage stage) {
        var keys = CHECKER_KEYS.get(Objects.requireNonNull(stage, "stage"));
        if (keys == null) {
            throw new IllegalArgumentException(stage + " is not a checker stage");
        }
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
        var tables = new ArrayList<Table>();
        tables.add(new Table(
                GENERATOR_PATH,
                List.of(
                        MAXIMUM_TYPE_DEPTH,
                        MAXIMUM_EXPRESSION_DEPTH,
                        MAXIMUM_NODES,
                        MAXIMUM_COLLECTION_SIZE,
                        MAXIMUM_STRING_BYTES,
                        MAXIMUM_INTEGER_BYTES,
                        IGNORED_CATEGORIES)));
        tables.add(new Table(WORKFLOW_PATH, List.of(WORKFLOW_MAXIMUM_ENTRIES)));
        tables.add(new Table(INPUTS_PATH, List.of(INPUTS_MAXIMUM_ENTRIES)));
        tables.add(new Table(
                stagePath(CorpusStage.PARSER),
                List.of(PARSER_MAXIMUM_ENTRIES, PARSER_TIMEOUT_SECONDS)));
        for (var stage : CorpusStage.checkerBranches()) {
            tables.add(new Table(stagePath(stage), checker(stage).all()));
        }
        tables.add(new Table(
                PBT_PATH,
                List.of(
                        MAXIMUM_INPUT_BYTES,
                        RICHNESS_COHORTS,
                        RICHNESS_NESTING_BASE,
                        RICHNESS_THRESHOLD_BASE)));
        return List.copyOf(tables);
    }

    private static Map<CorpusStage, CheckerKeys> checkerKeys() {
        var keys = new EnumMap<CorpusStage, CheckerKeys>(CorpusStage.class);
        for (var stage : CorpusStage.checkerBranches()) {
            var path = stagePath(stage);
            var documentation = CHECKER_DOCUMENTATION.get(stage);
            if (documentation == null) {
                throw new IllegalStateException(
                        "no configuration documentation for checker " + stage);
            }
            keys.put(
                    stage,
                    new CheckerKeys(
                            new Key<>(
                                    path,
                                    "max_entries",
                                    ConfigValueType.INTEGER,
                                    List.of(resultDirectoryDocumentation(stage)),
                                    config -> config.workflow()
                                            .checker(stage)
                                            .maximumEntries()),
                            new Key<>(
                                    path,
                                    "timeout_sec",
                                    ConfigValueType.INTEGER,
                                    List.of(CHECKER_TIMEOUT_DOCUMENTATION),
                                    config -> config.workflow()
                                            .checker(stage)
                                            .timeoutSeconds()),
                            new Key<>(
                                    path,
                                    "max_heap_mb",
                                    ConfigValueType.INTEGER,
                                    List.of(documentation.heap()),
                                    config -> config.workflow()
                                            .checker(stage)
                                            .maximumHeapMegabytes()),
                            new Key<>(
                                    path,
                                    "workers",
                                    ConfigValueType.INTEGER,
                                    documentation.workers(),
                                    config -> config.workflow().checker(stage).workers())));
        }
        return Map.copyOf(keys);
    }

    private static Key<Integer> generatorKey(
            String name, Function<FuzzTlaConfig, Integer> value) {
        return new Key<>(GENERATOR_PATH, name, ConfigValueType.INTEGER, List.of(), value);
    }

    /** Returns the table path of one stage, so a stage is named the same way everywhere. */
    private static String stagePath(CorpusStage stage) {
        return WORKFLOW_PATH + "." + stage.metadataName();
    }

    private static String resultDirectoryDocumentation(CorpusStage stage) {
        return "Maximum combined occupancy of the " + stage.displayName() + " result directories.";
    }
}
