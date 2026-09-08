package io.github.tlaplus.hardening.config;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** TOML codecs for library paths and ordered module selections. */
final class LibraryConfigValues {
    private static final String MODULE = "module";
    private static final String OPERATORS = "operators";
    private static final Gson STRINGS = new Gson();

    static final ConfigValueType<List<Path>> CLASSPATH = new ConfigValueType<>(
            (table, path, key) -> strings(array(table, path, key), path).stream()
                    .map(Path::of).toList(),
            paths -> formatStrings(paths.stream().map(Path::toString).toList()));

    static final ConfigValueType<List<OperatorLibraryConfig.Module>> MODULES = new ConfigValueType<>(
            LibraryConfigValues::readModules,
            modules -> modules.stream().map(module -> "{ " + MODULE + " = " + quote(module.module())
                    + ", " + OPERATORS + " = " + formatStrings(module.operators()) + " }")
                    .collect(Collectors.joining(", ", "[", "]")));

    private LibraryConfigValues() {}

    static String quote(String text) {
        // JSON basic strings are also TOML basic strings for these identifiers and paths.
        return STRINGS.toJson(text);
    }

    private static String formatStrings(List<String> strings) {
        return strings.stream().map(LibraryConfigValues::quote)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static TomlArray array(TomlTable table, String path, String key) throws ConfigException {
        if (!table.isArray(key)) {
            throw new ConfigException("expected '" + path + "' to be an array");
        }
        return table.getArray(key);
    }

    private static List<String> strings(TomlArray array, String path) throws ConfigException {
        var result = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text) || text.isBlank()) {
                throw new ConfigException("expected '" + path + "[" + i + "]' to be a nonempty string");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static List<OperatorLibraryConfig.Module> readModules(
            TomlTable table, String path, String key) throws ConfigException {
        var array = array(table, path, key);
        var result = new ArrayList<OperatorLibraryConfig.Module>();
        for (int i = 0; i < array.size(); i++) {
            var location = path + "[" + i + "]";
            if (!(array.get(i) instanceof TomlTable module)
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
}
