package io.github.tlaplus.hardening.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusDatabaseSchemaTest {
    private static final Path MANUAL = Path.of("docs", "manual", "corpus-database.md");
    private static final Pattern RELATION_HEADING =
            Pattern.compile("^### 2\\.\\d+\\. `(\\w+)`$");
    private static final Pattern COLUMN_ROW =
            Pattern.compile("^\\| `(\\w+)` \\| (INTEGER|REAL|TEXT) \\| (no|yes) \\|");

    /** A column as the manual documents it or as SQLite reports it. */
    private record DocumentedColumn(String name, String type, boolean nullable) {}

    @Test
    void manualDocumentsEveryTableAndViewColumn(@TempDir Path directory) throws Exception {
        var file = directory.resolve("schema.sqlite");
        try (var writer = CorpusDatabaseWriter.create(file)) {
            writer.commit();
        }

        var documented = documentedRelations();
        var actual = new LinkedHashMap<String, List<DocumentedColumn>>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.createStatement()) {
            var relations = new LinkedHashMap<String, String>();
            try (var result = statement.executeQuery(
                    "SELECT name, type FROM sqlite_schema WHERE type IN ('table', 'view') ORDER BY rowid")) {
                while (result.next()) {
                    relations.put(result.getString("name"), result.getString("type"));
                }
            }
            for (var relation : relations.entrySet()) {
                var isView = relation.getValue().equals("view");
                var columns = new ArrayList<DocumentedColumn>();
                try (var result = statement.executeQuery(
                        "PRAGMA table_info(" + relation.getKey() + ")")) {
                    while (result.next()) {
                        columns.add(new DocumentedColumn(
                                result.getString("name"),
                                result.getString("type"),
                                // SQLite reports no NOT NULL constraints for a view's columns.
                                isView
                                        ? documentedNullability(documented, relation.getKey(), result.getString("name"))
                                        : result.getInt("notnull") == 0));
                    }
                }
                actual.put(relation.getKey(), columns);
            }
        }

        assertEquals(documented, actual);
    }

    @Test
    void stageTableEndsWithOneColumnPerExplorationCountInDeclarationOrder() {
        var columns = DatabaseTable.STAGE.columns();
        var metricColumns = columns.subList(columns.size() - ExplorationCount.values().length, columns.size());

        assertEquals(
                Arrays.stream(ExplorationCount.values()).map(ExplorationCount::fieldName).toList(),
                metricColumns.stream().map(Column::name).toList());
        assertEquals(
                List.copyOf(DatabaseColumns.METRICS.values()), metricColumns);
    }

    @Test
    void recordsTheSchemaVersion(@TempDir Path directory) throws Exception {
        var file = directory.resolve("schema.sqlite");
        try (var writer = CorpusDatabaseWriter.create(file)) {
            writer.commit();
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var result = connection.createStatement().executeQuery("PRAGMA user_version")) {
            assertEquals(CorpusDatabaseSchema.VERSION, result.getInt(1));
        }
    }

    /** Reads the column tables of section 2 of the manual, keyed by table or view name. */
    private static Map<String, List<DocumentedColumn>> documentedRelations() throws Exception {
        var relations = new LinkedHashMap<String, List<DocumentedColumn>>();
        List<DocumentedColumn> current = null;
        for (var line : Files.readAllLines(MANUAL)) {
            var heading = RELATION_HEADING.matcher(line);
            if (heading.matches()) {
                current = new ArrayList<>();
                relations.put(heading.group(1), current);
                continue;
            }
            if (line.startsWith("#")) {
                current = null;
            }
            var row = COLUMN_ROW.matcher(line);
            if (current != null && row.find()) {
                current.add(new DocumentedColumn(row.group(1), row.group(2), row.group(3).equals("yes")));
            }
        }
        return relations;
    }

    private static boolean documentedNullability(
            Map<String, List<DocumentedColumn>> documented, String relation, String column) {
        return documented.getOrDefault(relation, List.of()).stream()
                .filter(candidate -> candidate.name().equals(column))
                .findFirst()
                .map(DocumentedColumn::nullable)
                .orElse(true);
    }
}
