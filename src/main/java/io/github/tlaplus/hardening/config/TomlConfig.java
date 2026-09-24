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
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;

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
    /** Stands in for an omitted optional table, whose keys all read their defaults. */
    private static final TomlTable EMPTY_TABLE = Toml.parse("");

    private TomlConfig() {}

    /** Reads and validates a complete configuration from {@code path}. */
    public static FuzzTlaConfig read(Path path) throws IOException, ConfigException {
        var result = parse(path);

        requireKeys(
                result,
                ConfigSchema.expectedKeys(ROOT_PATH),
                ConfigSchema.requiredKeys(ROOT_PATH),
                ROOT_LOCATION);
        var tables = resolveTables(result);
        for (var table : ConfigSchema.TABLES) {
            requireKeys(
                    tables.get(table.path()),
                    ConfigSchema.expectedKeys(table.path()),
                    ConfigSchema.requiredKeys(table.path()),
                    table.path());
        }

        try {
            return assemble(tables, path.toAbsolutePath().normalize().getParent());
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(exception.getMessage(), exception);
        }
    }

    /**
     * Reads a library file: a {@code [generator]} table holding exactly {@code classpath} and
     * {@code custom_operators}, with paths relative to the file's directory.
     */
    public static OperatorLibraryConfig readLibrary(Path path) throws IOException, ConfigException {
        var result = parse(path);
        var generator = ConfigSchema.CLASSPATH.tablePath();
        var keys = Set.of(ConfigSchema.CLASSPATH.name(), ConfigSchema.CUSTOM_OPERATORS.name());
        requireKeys(result, Set.of(generator), Set.of(generator), ROOT_LOCATION);
        var tables = Map.of(generator, requireTable(result, generator));
        requireKeys(tables.get(generator), keys, keys, generator);
        try {
            return new OperatorLibraryConfig(
                    ConfigSchema.CLASSPATH.read(tables), ConfigSchema.CUSTOM_OPERATORS.read(tables))
                    .relativeTo(path.toAbsolutePath().normalize().getParent());
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(exception.getMessage(), exception);
        }
    }

    private static TomlParseResult parse(Path path) throws IOException, ConfigException {
        var result = Toml.parse(path);
        if (result.hasErrors()) {
            var errors = result.errors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(System.lineSeparator()));
            throw new ConfigException("invalid TOML:" + System.lineSeparator() + errors);
        }
        return result;
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
     * reported as such rather than as a missing key of its parent. An omitted optional table
     * resolves to an empty one.
     */
    private static Map<String, TomlTable> resolveTables(TomlTable root) throws ConfigException {
        var tables = new HashMap<String, TomlTable>();
        tables.put(ROOT_PATH, root);
        for (var table : ConfigSchema.TABLES) {
            var parent = tables.get(table.parentPath());
            tables.put(
                    table.path(),
                    table.optional() && !parent.contains(table.name())
                            ? EMPTY_TABLE
                            : requireTable(parent, table.name()));
        }
        return tables;
    }

    /** Builds the configuration records from tables that have already passed key validation. */
    private static FuzzTlaConfig assemble(Map<String, TomlTable> tables, Path directory)
            throws ConfigException {
        var generatedKind = ConfigSchema.GENERATED_KIND.read(tables);
        var generationConfig = new IrGenerationConfig(
                ConfigSchema.GENERATOR_LIMITS.readExpressionLimits(tables),
                ConfigSchema.GENERATOR_LIMITS.readModuleLimits(tables),
                ConfigSchema.IGNORED_CATEGORIES.read(tables),
                ConfigSchema.FORM_WEIGHTS.read(tables), OperatorLibrary.empty());

        var checkers = new EnumMap<CorpusStage, CheckerStageConfig>(CorpusStage.class);
        for (var stage : CorpusStage.checkerBranches()) {
            checkers.put(stage, readChecker(stage, tables));
        }
        var workflowConfig = new WorkflowConfig(
                ConfigSchema.WORKFLOW_MAXIMUM_ENTRIES.read(tables),
                new InputStageConfig(
                        ConfigSchema.INPUTS_MAXIMUM_ENTRIES.read(tables),
                        ConfigSchema.KNOWN_DEFECTS.read(tables),
                        ConfigSchema.KNOWN_DEFECT_SAMPLES.read(tables))
                        .relativeTo(directory),
                new ParserStageConfig(
                        ConfigSchema.PARSER_MAXIMUM_ENTRIES.read(tables),
                        ConfigSchema.PARSER_TIMEOUT_SECONDS.read(tables)),
                checkers);

        var pbtConfig = new PbtConfig(
                ConfigSchema.MAXIMUM_INPUT_BYTES.read(tables),
                ConfigSchema.RICHNESS_COHORTS.read(tables),
                ConfigSchema.RICHNESS_NESTING_BASE.read(tables),
                ConfigSchema.RICHNESS_THRESHOLD_BASE.read(tables));

        var mutatorConfig = new MutatorConfig(
                ConfigSchema.GENERATION_SIZE.read(tables),
                ConfigSchema.FEEDBACK_RATIO.read(tables),
                ConfigSchema.MAXIMUM_EDITS.read(tables),
                ConfigSchema.OPERATOR_WEIGHTS.read(tables),
                new QualityGateConfig(
                        ConfigSchema.SELECT_FRACTION.read(tables),
                        ConfigSchema.SHALLOW_PATTERNS.read(tables),
                        ConfigSchema.CELL_CAPACITY.read(tables),
                        ConfigSchema.FEATURE_COVERAGE.read(tables)));

        var libraries = new OperatorLibraryConfig(
                ConfigSchema.CLASSPATH.read(tables), ConfigSchema.CUSTOM_OPERATORS.read(tables))
                .relativeTo(directory);
        return new FuzzTlaConfig(
                generatedKind, generationConfig, workflowConfig, pbtConfig, mutatorConfig, libraries);
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

    /**
     * Requires exactly the supported keys at one level of the document: every key of {@code
     * required} must be present, and no key outside {@code expected} may be.
     */
    private static void requireKeys(TomlTable table, Set<String> expected, Set<String> required,
            String location) throws ConfigException {
        var missing = required.stream()
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
