package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/** Reads and writes the strict {@code config.toml} format used by a corpus. */
public final class TomlConfig {
    private static final Set<String> ROOT_KEYS = Set.of("generator", "workflow", "pbt");
    private static final Set<String> GENERATOR_KEYS = Set.of(
            "maximum_type_depth",
            "maximum_expression_depth",
            "maximum_nodes",
            "maximum_collection_size",
            "maximum_string_bytes",
            "maximum_integer_bytes");
    private static final Set<String> WORKFLOW_KEYS =
            Set.of("maximum_entries", "inputs", "parser");
    private static final Set<String> INPUT_STAGE_KEYS = Set.of("maximum_entries");
    private static final Set<String> PARSER_STAGE_KEYS =
            Set.of("maximum_entries", "timeout_seconds");
    private static final Set<String> PBT_KEYS = Set.of(
            "maximum_input_bytes",
            "richness_cohorts",
            "richness_nesting_base",
            "richness_threshold_base");

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
        requireKeys(generator, GENERATOR_KEYS, "generator");
        requireKeys(workflow, WORKFLOW_KEYS, "workflow");
        requireKeys(inputs, INPUT_STAGE_KEYS, "workflow.inputs");
        requireKeys(parser, PARSER_STAGE_KEYS, "workflow.parser");
        requireKeys(pbt, PBT_KEYS, "pbt");

        try {
            var generationConfig = new IrGenerationConfig(
                    requireInt(generator, "generator.maximum_type_depth", "maximum_type_depth"),
                    requireInt(
                            generator,
                            "generator.maximum_expression_depth",
                            "maximum_expression_depth"),
                    requireInt(generator, "generator.maximum_nodes", "maximum_nodes"),
                    requireInt(
                            generator,
                            "generator.maximum_collection_size",
                            "maximum_collection_size"),
                    requireInt(
                            generator,
                            "generator.maximum_string_bytes",
                            "maximum_string_bytes"),
                    requireInt(
                            generator,
                            "generator.maximum_integer_bytes",
                            "maximum_integer_bytes"));
            var workflowConfig = new WorkflowConfig(
                    requireInt(workflow, "workflow.maximum_entries", "maximum_entries"),
                    new StageConfig(requireInt(
                            inputs,
                            "workflow.inputs.maximum_entries",
                            "maximum_entries")),
                    new ParserConfig(
                            requireInt(
                                    parser,
                                    "workflow.parser.maximum_entries",
                                    "maximum_entries"),
                            requireInt(
                                    parser,
                                    "workflow.parser.timeout_seconds",
                                    "timeout_seconds")));
            var pbtConfig = new PbtConfig(
                    requireInt(pbt, "pbt.maximum_input_bytes", "maximum_input_bytes"),
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
        return """
                [generator]
                maximum_type_depth = %d
                maximum_expression_depth = %d
                maximum_nodes = %d
                maximum_collection_size = %d
                maximum_string_bytes = %d
                maximum_integer_bytes = %d

                [workflow]
                # Maximum number of unique entries across every workflow directory.
                maximum_entries = %d

                [workflow.inputs]
                # Maximum current occupancy of 00-inputs.
                maximum_entries = %d

                [workflow.parser]
                # Maximum combined occupancy of the parser result directories.
                maximum_entries = %d
                # Wall-clock limit for parsing one generated specification.
                timeout_seconds = %d

                [pbt]
                # Inclusive upper bound on a randomly generated input's length.
                maximum_input_bytes = %d
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
                        workflow.maximumEntries(),
                        workflow.inputs().maximumEntries(),
                        workflow.parser().maximumEntries(),
                        workflow.parser().timeoutSeconds(),
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
}
