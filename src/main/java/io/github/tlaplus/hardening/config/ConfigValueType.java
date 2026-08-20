package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.Arrays;
import java.util.EnumSet;
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

    static final ConfigValueType<Integer> INTEGER =
            new ConfigValueType<>(ConfigValueType::readInt, String::valueOf);

    static final ConfigValueType<Double> NUMBER =
            new ConfigValueType<>(ConfigValueType::readDouble, Object::toString);

    static final ConfigValueType<Set<ExpressionCategory>> CATEGORIES = new ConfigValueType<>(
            ConfigValueType::readCategories, ConfigValueType::formatCategories);

    /** Reads one TOML integer and narrows it only when it fits in a Java {@code int}. */
    private static int readInt(TomlTable table, String path, String key) throws ConfigException {
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

    /** Renders a category list in {@link ExpressionCategory} declaration order. */
    private static String formatCategories(Set<ExpressionCategory> categories) {
        return Arrays.stream(ExpressionCategory.values())
                .filter(categories::contains)
                .map(category -> '"' + category.configName() + '"')
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
