package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.engine.CustomExpressionKind;
import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * How the value of one configuration key is read from TOML and written back as TOML syntax.
 *
 * <p>A key declares its type once, in {@link ConfigSchema}, and reading and rendering both follow
 * from that declaration. Supporting a new kind of value is a new constant here.
 */
record ConfigValueType<T>(Reader<T> reader, Function<T, String> format) {
    /** Reads one value, naming it by its full document path in any diagnostic. */
    @FunctionalInterface
    interface Reader<T> {
        T read(TomlTable table, String path, String key) throws ConfigException;
    }

    private static final String MODULE = "module";
    private static final String OPERATORS = "operators";

    private static final Map<String, ExpressionCategory> CATEGORIES_BY_CONFIG_NAME =
            Arrays.stream(ExpressionCategory.values())
                    .collect(Collectors.toUnmodifiableMap(
                            ExpressionCategory::configName, category -> category));

    private static final Map<String, ExpressionKind> KINDS_BY_CONFIG_NAME =
            ExpressionKind.all().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            ExpressionKind::configName, kind -> kind));

    static final ConfigValueType<Integer> INTEGER =
            new ConfigValueType<>(ConfigValueType::readInt, String::valueOf);

    static final ConfigValueType<Double> NUMBER =
            new ConfigValueType<>(ConfigValueType::readDouble, Object::toString);

    static final ConfigValueType<Set<ExpressionCategory>> CATEGORIES = new ConfigValueType<>(
            ConfigValueType::readCategories, ConfigValueType::formatCategories);

    static final ConfigValueType<Map<ExpressionKind, Integer>> WEIGHTS = new ConfigValueType<>(
            ConfigValueType::readWeights, ConfigValueType::formatWeights);

    static final ConfigValueType<InputKind> INPUT_KIND = new ConfigValueType<>(
            ConfigValueType::readInputKind, kind -> quote(kind.encodedName()));

    static final ConfigValueType<List<Path>> CLASSPATH = new ConfigValueType<>(
            (table, path, key) -> strings(array(table, path, key), path).stream()
                    .map(Path::of).toList(),
            paths -> formatList(paths.stream().map(Path::toString).toList()));

    static final ConfigValueType<List<OperatorLibraryConfig.Module>> MODULES = new ConfigValueType<>(
            ConfigValueType::readModules,
            modules -> modules.stream()
                    .map(module -> "{ " + MODULE + " = " + quote(module.module())
                            + ", " + OPERATORS + " = " + formatList(module.operators()) + " }")
                    .collect(Collectors.joining(", ", "[", "]")));

    /** Reads one TOML integer and narrows it only when it fits in a Java {@code int}. */
    private static int readInt(TomlTable table, String path, String key) throws ConfigException {
        var literalKey = java.util.List.of(key);
        if (!table.isLong(literalKey)) {
            throw new ConfigException("expected '" + path + "' to be an integer");
        }
        try {
            return Math.toIntExact(table.getLong(literalKey));
        } catch (ArithmeticException exception) {
            throw new ConfigException(
                    "'" + path + "' is outside the supported integer range", exception);
        }
    }

    /** Reads a number, accepting an integer where a fractional value is allowed. */
    private static double readDouble(TomlTable table, String path, String key)
            throws ConfigException {
        if (table.isDouble(key)) {
            return table.getDouble(key);
        }
        if (table.isLong(key)) {
            return table.getLong(key);
        }
        throw new ConfigException("expected '" + path + "' to be a number");
    }

    /** Reads one TOML array, naming it by its document path in any diagnostic. */
    private static TomlArray array(TomlTable table, String path, String key) throws ConfigException {
        if (!table.isArray(key)) {
            throw new ConfigException("expected '" + path + "' to be an array");
        }
        return table.getArray(key);
    }

    /** Reads the elements of an array as nonempty strings. */
    private static List<String> strings(TomlArray array, String path) throws ConfigException {
        var result = new ArrayList<String>(array.size());
        for (var index = 0; index < array.size(); index++) {
            if (!(array.get(index) instanceof String text) || text.isBlank()) {
                throw new ConfigException(
                        "expected '" + path + "[" + index + "]' to be a nonempty string");
            }
            result.add(text);
        }
        return result;
    }

    private static Set<ExpressionCategory> readCategories(
            TomlTable table, String path, String key) throws ConfigException {
        var categories = EnumSet.noneOf(ExpressionCategory.class);
        for (var name : strings(array(table, path, key), path)) {
            var category = CATEGORIES_BY_CONFIG_NAME.get(name);
            if (category == null) {
                throw new ConfigException(
                        "unknown expression category '" + name + "' in '" + path + "'");
            }
            categories.add(category);
        }
        return Set.copyOf(categories);
    }

    /** Reads the ordered module selections, each an inline table of a name and its operators. */
    private static List<OperatorLibraryConfig.Module> readModules(
            TomlTable table, String path, String key) throws ConfigException {
        var array = array(table, path, key);
        var result = new ArrayList<OperatorLibraryConfig.Module>(array.size());
        for (var index = 0; index < array.size(); index++) {
            var location = path + "[" + index + "]";
            if (!(array.get(index) instanceof TomlTable module)
                    || !module.keySet().equals(Set.of(MODULE, OPERATORS))
                    || !module.isString(MODULE)) {
                throw new ConfigException("expected '" + location
                        + "' to contain exactly module (string) and operators (array)");
            }
            result.add(new OperatorLibraryConfig.Module(module.getString(MODULE),
                    strings(array(module, location + "." + OPERATORS, OPERATORS), location)));
        }
        return List.copyOf(result);
    }

    /**
     * Reads a table of form names to slot counts. An absent form keeps the default weight, so
     * only the forms a corpus actually biases need to appear.
     */
    private static Map<ExpressionKind, Integer> readWeights(
            TomlTable table, String path, String key) throws ConfigException {
        if (!table.isTable(key)) {
            throw new ConfigException("expected '" + path + "' to be a table");
        }

        var weights = new HashMap<ExpressionKind, Integer>();
        var entries = table.getTable(key);
        for (var name : entries.keySet()) {
            var kind = KINDS_BY_CONFIG_NAME.get(name);
            if (kind == null && name.contains("!")) {
                kind = new CustomExpressionKind(OperatorId.parse(name));
            }
            if (kind == null) {
                throw new ConfigException(
                        "unknown expression kind '" + name + "' in '" + path + "'");
            }
            weights.put(kind, readInt(entries, path + "." + name, name));
        }
        return Map.copyOf(weights);
    }

    /**
     * Renders the weights in the order {@code IrGenerationConfig} normalizes them into: catalog
     * order, then custom operators. A custom name is not a bare TOML key, so it is quoted.
     */
    private static String formatWeights(Map<ExpressionKind, Integer> weights) {
        if (weights.isEmpty()) {
            return "{}";
        }
        return weights.entrySet().stream()
                .map(entry -> (entry.getKey() instanceof CustomExpressionKind
                                ? quote(entry.getKey().configName())
                                : entry.getKey().configName())
                        + " = " + entry.getValue())
                .collect(Collectors.joining(", ", "{ ", " }"));
    }

    /** Reads the kind of input a run generates, by the same name the corpus stores. */
    private static InputKind readInputKind(TomlTable table, String path, String key)
            throws ConfigException {
        if (!table.isString(key)) {
            throw new ConfigException("expected '" + path + "' to be a string");
        }
        var name = table.getString(key);
        return InputKind.fromEncodedName(name)
                .orElseThrow(() -> new ConfigException(
                        "unknown input kind '"
                                + name
                                + "' in '"
                                + path
                                + "'; expected one of "
                                + Arrays.stream(InputKind.values())
                                        .map(InputKind::encodedName)
                                        .collect(Collectors.joining(", "))));
    }

    /** Renders a category list in {@link ExpressionCategory} declaration order. */
    private static String formatCategories(Set<ExpressionCategory> categories) {
        return formatList(Arrays.stream(ExpressionCategory.values())
                .filter(categories::contains)
                .map(ExpressionCategory::configName)
                .toList());
    }

    private static String formatList(List<String> values) {
        return values.stream().map(ConfigValueType::quote)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Renders one TOML basic string. Every value written back is an identifier, an encoded name or
     * a path, so escaping the two characters a basic string reserves is enough.
     */
    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
