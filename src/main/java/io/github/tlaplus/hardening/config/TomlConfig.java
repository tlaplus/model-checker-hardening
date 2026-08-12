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
    private static final Set<String> ROOT_KEYS = Set.of("generator", "pbt");
    private static final Set<String> GENERATOR_KEYS = Set.of(
            "maximum_type_depth",
            "maximum_expression_depth",
            "maximum_nodes",
            "maximum_collection_size",
            "maximum_string_bytes",
            "maximum_integer_bytes");
    private static final Set<String> PBT_KEYS =
            Set.of("corpus_entries", "maximum_input_bytes");

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
        var pbt = requireTable(result, "pbt");
        requireKeys(generator, GENERATOR_KEYS, "generator");
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
            var pbtConfig = new PbtConfig(
                    requireInt(pbt, "pbt.corpus_entries", "corpus_entries"),
                    requireInt(pbt, "pbt.maximum_input_bytes", "maximum_input_bytes"));
            return new FuzzTlaConfig(generationConfig, pbtConfig);
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
        var pbt = config.pbt();
        return """
                [generator]
                maximum_type_depth = %d
                maximum_expression_depth = %d
                maximum_nodes = %d
                maximum_collection_size = %d
                maximum_string_bytes = %d
                maximum_integer_bytes = %d

                [pbt]
                # Target number of unique, accepted inputs in 00inputs.
                corpus_entries = %d
                # Inclusive upper bound on a randomly generated input's length.
                maximum_input_bytes = %d
                """
                .formatted(
                        generator.maximumTypeDepth(),
                        generator.maximumExpressionDepth(),
                        generator.maximumNodes(),
                        generator.maximumCollectionSize(),
                        generator.maximumStringBytes(),
                        generator.maximumIntegerBytes(),
                        pbt.corpusEntries(),
                        pbt.maximumInputBytes());
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
}
