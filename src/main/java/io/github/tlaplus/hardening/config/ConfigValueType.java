package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import io.github.tlaplus.hardening.gen.engine.CustomExpressionKind;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
            ConfigValueType::readInputKind, kind -> '"' + kind.encodedName() + '"');

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

    private static Set<ExpressionCategory> readCategories(
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
            var category = CATEGORIES_BY_CONFIG_NAME.get(name);
            if (category == null) {
                throw new ConfigException(
                        "unknown expression category '" + name + "' in '" + path + "'");
            }
            categories.add(category);
        }
        return Set.copyOf(categories);
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

    /** Renders the weights in expression catalog order. */
    private static String formatWeights(Map<ExpressionKind, Integer> weights) {
        if (weights.isEmpty()) {
            return "{}";
        }
        return java.util.stream.Stream.concat(
                        ExpressionKind.all().stream().filter(weights::containsKey),
                        weights.keySet().stream().filter(CustomExpressionKind.class::isInstance)
                                .sorted(java.util.Comparator.comparing(ExpressionKind::configName)))
                .map(kind -> (kind instanceof CustomExpressionKind
                        ? LibraryConfigValues.quote(kind.configName()) : kind.configName())
                        + " = " + weights.get(kind))
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
        return Arrays.stream(ExpressionCategory.values())
                .filter(categories::contains)
                .map(category -> '"' + category.configName() + '"')
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
