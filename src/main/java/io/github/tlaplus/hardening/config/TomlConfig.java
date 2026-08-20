package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/**
 * Reads and writes the strict {@code config.toml} format used by a corpus.
 *
 * <p>What the format contains is declared once in {@link ConfigSchema}; this class only parses a
 * file against those declarations, assembles the configuration records, and writes them back.
 */
public final class TomlConfig {
    /** The path {@link ConfigSchema} uses for the root of the document. */
    private static final String ROOT_PATH = "";

    /** The name the root of the document is called by in diagnostics. */
    private static final String ROOT_LOCATION = "root";

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

        requireKeys(result, ConfigSchema.expectedKeys(ROOT_PATH), ROOT_LOCATION);
        var tables = resolveTables(result);
        for (var table : ConfigSchema.TABLES) {
            requireKeys(
                    tables.get(table.path()),
                    ConfigSchema.expectedKeys(table.path()),
                    table.path());
        }

        try {
            return assemble(tables);
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
        return ConfigSchema.render(config);
    }

    /**
     * Resolves every declared table, reporting a table that is missing or has the wrong shape.
     *
     * <p>Every table is resolved before any table's keys are validated, so a missing table is
     * reported as such rather than as a missing key of its parent.
     */
    private static Map<String, TomlTable> resolveTables(TomlTable root) throws ConfigException {
        var tables = new HashMap<String, TomlTable>();
        tables.put(ROOT_PATH, root);
        for (var table : ConfigSchema.TABLES) {
            tables.put(
                    table.path(),
                    requireTable(tables.get(table.parentPath()), table.name()));
        }
        return tables;
    }

    /** Builds the configuration records from tables that have already passed key validation. */
    private static FuzzTlaConfig assemble(Map<String, TomlTable> tables) throws ConfigException {
        var generationConfig = new IrGenerationConfig(
                ConfigSchema.MAXIMUM_TYPE_DEPTH.read(tables),
                ConfigSchema.MAXIMUM_EXPRESSION_DEPTH.read(tables),
                ConfigSchema.MAXIMUM_NODES.read(tables),
                ConfigSchema.MAXIMUM_COLLECTION_SIZE.read(tables),
                ConfigSchema.MAXIMUM_STRING_BYTES.read(tables),
                ConfigSchema.MAXIMUM_INTEGER_BYTES.read(tables),
                ConfigSchema.IGNORED_CATEGORIES.read(tables));

        var checkers = new EnumMap<CorpusStage, CheckerStageConfig>(CorpusStage.class);
        for (var stage : CorpusStage.checkerBranches()) {
            checkers.put(stage, readChecker(stage, tables));
        }
        var workflowConfig = new WorkflowConfig(
                ConfigSchema.WORKFLOW_MAXIMUM_ENTRIES.read(tables),
                new StageConfig(ConfigSchema.INPUTS_MAXIMUM_ENTRIES.read(tables)),
                new ParserStageConfig(
                        ConfigSchema.PARSER_MAXIMUM_ENTRIES.read(tables),
                        ConfigSchema.PARSER_TIMEOUT_SECONDS.read(tables)),
                checkers);

        var pbtConfig = new PbtConfig(
                ConfigSchema.MAXIMUM_INPUT_BYTES.read(tables),
                ConfigSchema.RICHNESS_COHORTS.read(tables),
                ConfigSchema.RICHNESS_NESTING_BASE.read(tables),
                ConfigSchema.RICHNESS_THRESHOLD_BASE.read(tables));

        return new FuzzTlaConfig(generationConfig, workflowConfig, pbtConfig);
    }

    /** Reads one checker table, naming the stage in every diagnostic. */
    private static CheckerStageConfig readChecker(
            CorpusStage stage, Map<String, TomlTable> tables) throws ConfigException {
        var keys = ConfigSchema.checker(stage);
        return new CheckerStageConfig(
                keys.maximumEntries().read(tables),
                keys.timeoutSeconds().read(tables),
                keys.maximumHeapMegabytes().read(tables),
                keys.workers().read(tables));
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
}
