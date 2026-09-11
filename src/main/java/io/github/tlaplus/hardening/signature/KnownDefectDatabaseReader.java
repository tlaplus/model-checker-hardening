package io.github.tlaplus.hardening.signature;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads one database file: a TOML document of {@code [[signature]]} tables with exactly the keys
 * {@code id}, {@code references}, {@code description}, and {@code match}.
 *
 * <p>Like {@code config.toml}, the format is strict: a missing or unknown key is an error. Every
 * diagnostic names the file, and the signature and pattern it concerns.
 */
final class KnownDefectDatabaseReader {
    private static final String SIGNATURE = "signature";
    private static final String ID = "id";
    private static final String REFERENCES = "references";
    private static final String DESCRIPTION = "description";
    private static final String MATCH = "match";
    private static final Set<String> KEYS = Set.of(ID, REFERENCES, DESCRIPTION, MATCH);

    private KnownDefectDatabaseReader() {}

    static List<KnownDefect> read(Path file) throws KnownDefectDatabaseException {
        Objects.requireNonNull(file, "file");
        final TomlParseResult document;
        try {
            document = Toml.parse(file);
        } catch (IOException exception) {
            throw new KnownDefectDatabaseException(
                    "cannot read known-defect database " + file + ": " + Diagnostics.message(exception),
                    exception);
        }
        return read(document, file.toString());
    }

    /** Reads a database from its source text; {@code origin} names it in diagnostics. */
    static List<KnownDefect> parse(String source, String origin) throws KnownDefectDatabaseException {
        return read(Toml.parse(Objects.requireNonNull(source, "source")), origin);
    }

    private static List<KnownDefect> read(TomlParseResult document, String origin)
            throws KnownDefectDatabaseException {
        if (document.hasErrors()) {
            throw new KnownDefectDatabaseException(origin + ": invalid TOML: " + document.errors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("; ")));
        }
        var unknown = document.keySet().stream().filter(key -> !key.equals(SIGNATURE)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new KnownDefectDatabaseException(origin + ": unknown keys: " + String.join(", ", unknown));
        }
        if (!document.contains(SIGNATURE)) {
            return List.of();
        }
        if (!document.isArray(SIGNATURE)) {
            throw new KnownDefectDatabaseException(origin + ": expected [[signature]] tables");
        }
        var tables = document.getArrayOrEmpty(SIGNATURE);
        var result = new ArrayList<KnownDefect>(tables.size());
        for (var index = 0; index < tables.size(); index++) {
            if (!(tables.get(index) instanceof TomlTable table)) {
                throw new KnownDefectDatabaseException(origin + ": expected [[signature]] tables");
            }
            result.add(signature(table, origin + ": signature " + (index + 1)));
        }
        return List.copyOf(result);
    }

    private static KnownDefect signature(TomlTable table, String location)
            throws KnownDefectDatabaseException {
        var missing = KEYS.stream().filter(key -> !table.keySet().contains(key)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new KnownDefectDatabaseException(location + ": missing keys: " + String.join(", ", missing));
        }
        var unknown = table.keySet().stream().filter(key -> !KEYS.contains(key)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new KnownDefectDatabaseException(location + ": unknown keys: " + String.join(", ", unknown));
        }
        var id = string(table, ID, location);
        var named = location + " ('" + id + "')";
        var references = strings(table, REFERENCES, named);
        var description = string(table, DESCRIPTION, named);
        var sources = strings(table, MATCH, named);
        var alternatives = new ArrayList<IrPattern>(sources.size());
        for (var index = 0; index < sources.size(); index++) {
            try {
                alternatives.add(PatternParser.parse(sources.get(index)));
            } catch (PatternException exception) {
                throw new KnownDefectDatabaseException(
                        named + ": " + MATCH + "[" + index + "]: " + exception.getMessage(), exception);
            }
        }
        try {
            return new KnownDefect(id, references, description, alternatives);
        } catch (IllegalArgumentException exception) {
            throw new KnownDefectDatabaseException(location + ": " + exception.getMessage(), exception);
        }
    }

    private static String string(TomlTable table, String key, String location)
            throws KnownDefectDatabaseException {
        if (!table.isString(List.of(key))) {
            throw new KnownDefectDatabaseException(location + ": expected '" + key + "' to be a string");
        }
        return table.getString(List.of(key));
    }

    private static List<String> strings(TomlTable table, String key, String location)
            throws KnownDefectDatabaseException {
        if (!table.isArray(List.of(key))) {
            throw new KnownDefectDatabaseException(location + ": expected '" + key + "' to be an array");
        }
        TomlArray array = table.getArray(List.of(key));
        var result = new ArrayList<String>(array.size());
        for (var index = 0; index < array.size(); index++) {
            if (!(array.get(index) instanceof String text)) {
                throw new KnownDefectDatabaseException(
                        location + ": expected '" + key + "[" + index + "]' to be a string");
            }
            result.add(text);
        }
        if (result.isEmpty()) {
            throw new KnownDefectDatabaseException(location + ": '" + key + "' must not be empty");
        }
        return result;
    }
}
