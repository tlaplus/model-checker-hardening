package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/** Reads and writes the strict {@code config.toml} format used by a corpus. */
public final class TomlConfig {
    private static final Set<String> ROOT_KEYS = Set.of("generator", "workflow", "pbt");
    private static final Set<String> GENERATOR_KEYS = Set.of(
            "max_type_depth",
            "max_expression_depth",
            "max_nodes",
            "max_collection_size",
            "max_string_bytes",
            "max_integer_bytes",
            "ignore");
    private static final Map<String, ExpressionCategory> GENERATOR_IGNORE_VALUES =
            Arrays.stream(ExpressionCategory.values())
                    .collect(Collectors.toUnmodifiableMap(
                            ExpressionCategory::configName, category -> category));
    private static final Set<String> WORKFLOW_KEYS = workflowKeys();
    private static final Set<String> INPUT_STAGE_KEYS = Set.of("max_entries");
    private static final Set<String> PARSER_STAGE_KEYS =
            Set.of("max_entries", "timeout_sec");
    private static final Set<String> CHECKER_STAGE_KEYS = Set.of(
            "max_entries", "timeout_sec", "max_heap_mb", "workers");
    private static final Set<String> PBT_KEYS = Set.of(
            "max_input_bytes",
            "richness_cohorts",
            "richness_nesting_base",
            "richness_threshold_base");

    /** The workflow table holds the global limit, the input stage, and one table per stage. */
    private static Set<String> workflowKeys() {
        var keys = new HashSet<String>(Set.of("max_entries", "inputs"));
        for (var stage : CorpusStage.values()) {
            keys.add(stage.metadataName());
        }
        return Set.copyOf(keys);
    }

    private TomlConfig() {}

    /** Reads and validates a complete configuration from {@code path}. */
    public static FuzzTlaConfig read(Path path) throws IOException, ConfigException {
        var result = Toml.parse(path);
        if (result.hasErrors()) {
            var errors = result.errors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(System.lineSeparator()));
            throw new ConfigException("invalid TOML:" + System.lineSeparator() + errors);
        }

        requireKeys(result, ROOT_KEYS, "root");
        var generator = requireTable(result, "generator");
        var workflow = requireTable(result, "workflow");
        var inputs = requireTable(workflow, "inputs");
        var parser = requireTable(workflow, "parser");
        var pbt = requireTable(result, "pbt");
        var checkerTables = new EnumMap<CorpusStage, TomlTable>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            checkerTables.put(checker, requireTable(workflow, checker.metadataName()));
        }
        requireKeys(generator, GENERATOR_KEYS, "generator");
        requireKeys(workflow, WORKFLOW_KEYS, "workflow");
        requireKeys(inputs, INPUT_STAGE_KEYS, "workflow.inputs");
        requireKeys(parser, PARSER_STAGE_KEYS, "workflow.parser");
        requireKeys(pbt, PBT_KEYS, "pbt");
        for (var entry : checkerTables.entrySet()) {
            requireKeys(
                    entry.getValue(),
                    CHECKER_STAGE_KEYS,
                    "workflow." + entry.getKey().metadataName());
        }

        try {
            var generationConfig = new IrGenerationConfig(
                    requireInt(generator, "generator.max_type_depth", "max_type_depth"),
                    requireInt(
                            generator,
                            "generator.max_expression_depth",
                            "max_expression_depth"),
                    requireInt(generator, "generator.max_nodes", "max_nodes"),
                    requireInt(
                            generator,
                            "generator.max_collection_size",
                            "max_collection_size"),
                    requireInt(
                            generator,
                            "generator.max_string_bytes",
                            "max_string_bytes"),
                    requireInt(
                            generator,
                            "generator.max_integer_bytes",
                            "max_integer_bytes"),
                    requireExpressionCategories(generator, "generator.ignore", "ignore"));
            var maximumEntries =
                    requireInt(workflow, "workflow.max_entries", "max_entries");
            var checkers = new EnumMap<CorpusStage, CheckerStageConfig>(CorpusStage.class);
            for (var entry : checkerTables.entrySet()) {
                checkers.put(entry.getKey(), readChecker(entry.getKey(), entry.getValue()));
            }
            var workflowConfig = new WorkflowConfig(
                    maximumEntries,
                    new StageConfig(requireInt(
                            inputs,
                            "workflow.inputs.max_entries",
                            "max_entries")),
                    new ParserStageConfig(
                            requireInt(
                                    parser,
                                    "workflow.parser.max_entries",
                                    "max_entries"),
                            requireInt(
                                    parser,
                                    "workflow.parser.timeout_sec",
                                    "timeout_sec")),
                    checkers);
            var pbtConfig = new PbtConfig(
                    requireInt(pbt, "pbt.max_input_bytes", "max_input_bytes"),
                    requireInt(pbt, "pbt.richness_cohorts", "richness_cohorts"),
                    requireDouble(
                            pbt,
                            "pbt.richness_nesting_base",
                            "richness_nesting_base"),
                    requireDouble(
                            pbt,
                            "pbt.richness_threshold_base",
                            "richness_threshold_base"));
            return new FuzzTlaConfig(generationConfig, workflowConfig, pbtConfig);
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(exception.getMessage(), exception);
        }
    }

    /** Reads one checker table, naming the stage in every diagnostic. */
    private static CheckerStageConfig readChecker(CorpusStage stage, TomlTable table)
            throws ConfigException {
        var path = "workflow." + stage.metadataName() + ".";
        return new CheckerStageConfig(
                requireInt(table, path + "max_entries", "max_entries"),
                requireInt(table, path + "timeout_sec", "timeout_sec"),
                requireInt(table, path + "max_heap_mb", "max_heap_mb"),
                requireInt(table, path + "workers", "workers"));
    }

    /** Writes {@code config} as UTF-8 without replacing an existing file. */
    public static void writeNew(Path path, FuzzTlaConfig config) throws IOException {
        Files.writeString(
                path,
                render(config),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    /** Renders the complete configuration with brief descriptions of user-facing fields. */
    public static String render(FuzzTlaConfig config) {
        var generator = config.generator();
        var workflow = config.workflow();
        var pbt = config.pbt();
        var ignoredCategories = Arrays.stream(ExpressionCategory.values())
                .filter(generator.ignoredCategories()::contains)
                .map(category -> '"' + category.configName() + '"')
                .collect(Collectors.joining(", ", "[", "]"));
        return """
                [generator]
                max_type_depth = %d
                max_expression_depth = %d
                max_nodes = %d
                max_collection_size = %d
                max_string_bytes = %d
                max_integer_bytes = %d
                ignore = %s

                [workflow]
                # Maximum number of unique entries across every workflow directory.
                max_entries = %d

                [workflow.inputs]
                # Maximum current occupancy of 00-inputs.
                max_entries = %d

                [workflow.parser]
                # Maximum combined occupancy of the parser result directories.
                max_entries = %d
                # Wall-clock limit for parsing one generated specification.
                timeout_sec = %d

                [workflow.tlc]
                # Maximum combined occupancy of the TLC result directories.
                max_entries = %d
                # Wall-clock limit for checking one generated specification.
                timeout_sec = %d
                # Maximum heap allocated to each isolated TLC JVM.
                max_heap_mb = %d
                # Number of TLC model-checking workers in each isolated JVM.
                workers = %d

                [workflow.apalache]
                # Maximum combined occupancy of the Apalache result directories.
                max_entries = %d
                # Wall-clock limit for checking one generated specification.
                timeout_sec = %d
                # Maximum heap allocated to each persistent Apalache worker JVM.
                max_heap_mb = %d
                # Number of concurrent FuzzTLA Apalache workers.
                # Initialized to half the available processors, rounded down (at least one).
                workers = %d

                [pbt]
                # Inclusive upper bound on a randomly generated input's length.
                max_input_bytes = %d
                # Number of uniformly selected collection-richness cohorts.
                richness_cohorts = %d
                # Weight multiplier for each level of collection nesting.
                richness_nesting_base = %s
                # Growth factor for successive cohort admission thresholds.
                richness_threshold_base = %s
                """
                .formatted(
                        generator.maximumTypeDepth(),
                        generator.maximumExpressionDepth(),
                        generator.maximumNodes(),
                        generator.maximumCollectionSize(),
                        generator.maximumStringBytes(),
                        generator.maximumIntegerBytes(),
                        ignoredCategories,
                        workflow.maximumEntries(),
                        workflow.inputs().maximumEntries(),
                        workflow.parser().maximumEntries(),
                        workflow.parser().timeoutSeconds(),
                        workflow.checker(CorpusStage.TLC).maximumEntries(),
                        workflow.checker(CorpusStage.TLC).timeoutSeconds(),
                        workflow.checker(CorpusStage.TLC).maximumHeapMegabytes(),
                        workflow.checker(CorpusStage.TLC).workers(),
                        workflow.checker(CorpusStage.APALACHE).maximumEntries(),
                        workflow.checker(CorpusStage.APALACHE).timeoutSeconds(),
                        workflow.checker(CorpusStage.APALACHE).maximumHeapMegabytes(),
                        workflow.checker(CorpusStage.APALACHE).workers(),
                        pbt.maximumInputBytes(),
                        pbt.richnessCohorts(),
                        Double.toString(pbt.richnessNestingBase()),
                        Double.toString(pbt.richnessThresholdBase()));
    }

    /** Returns a required table or reports that its value has the wrong shape. */
    private static TomlTable requireTable(TomlTable parent, String key) throws ConfigException {
        if (!parent.isTable(key)) {
            throw new ConfigException("expected '" + key + "' to be a table");
        }
        return parent.getTable(key);
    }

    /** Requires exactly the supported keys at one level of the document. */
    private static void requireKeys(TomlTable table, Set<String> expected, String location)
            throws ConfigException {
        var missing = expected.stream()
                .filter(key -> !table.keySet().contains(key))
                .sorted()
                .toList();
        var unknown = table.keySet().stream()
                .filter(key -> !expected.contains(key))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new ConfigException(
                    "missing " + location + " keys: " + String.join(", ", missing));
        }
        if (!unknown.isEmpty()) {
            throw new ConfigException(
                    "unknown " + location + " keys: " + String.join(", ", unknown));
        }
    }

    /** Reads one TOML integer and narrows it only when it fits in a Java {@code int}. */
    private static int requireInt(TomlTable table, String path, String key) throws ConfigException {
        if (!table.isLong(key)) {
            throw new ConfigException("expected '" + path + "' to be an integer");
        }
        try {
            return Math.toIntExact(table.getLong(key));
        } catch (ArithmeticException exception) {
            throw new ConfigException(
                    "'" + path + "' is outside the supported integer range", exception);
        }
    }

    private static double requireDouble(TomlTable table, String path, String key)
            throws ConfigException {
        if (table.isDouble(key)) {
            return table.getDouble(key);
        }
        if (table.isLong(key)) {
            return table.getLong(key);
        }
        throw new ConfigException("expected '" + path + "' to be a number");
    }

    private static Set<ExpressionCategory> requireExpressionCategories(
            TomlTable table, String path, String key) throws ConfigException {
        if (!table.isArray(key)) {
            throw new ConfigException("expected '" + path + "' to be an array");
        }

        var array = table.getArray(key);
        var categories = EnumSet.noneOf(ExpressionCategory.class);
        for (var index = 0; index < array.size(); index++) {
            if (!(array.get(index) instanceof String name)) {
                throw new ConfigException(
                        "expected '" + path + "[" + index + "]' to be a string");
            }
            var category = GENERATOR_IGNORE_VALUES.get(name);
            if (category == null) {
                throw new ConfigException(
                        "unknown expression category '" + name + "' in '" + path + "'");
            }
            categories.add(category);
        }
        return Set.copyOf(categories);
    }
}
