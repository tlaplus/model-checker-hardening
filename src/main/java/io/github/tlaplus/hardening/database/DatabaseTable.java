package io.github.tlaplus.hardening.database;

import static io.github.tlaplus.hardening.database.DatabaseColumns.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A table of the corpus database: its name, columns and keys. {@code entry} is keyed by its row id;
 * every other table is stored {@code WITHOUT ROWID} in the B-tree of its primary key.
 */
enum DatabaseTable {
    EXPORT("export", List.of(KEY, VALUE), List.of(KEY), List.of(), false),
    ENTRY(
            "entry",
            List.of(
                    ID,
                    DIRECTORY,
                    HASH,
                    KIND,
                    INPUT_BYTES,
                    COHORT,
                    RICHNESS,
                    EVALUATED_NODES,
                    REPLAY_ERROR),
            List.of(ID),
            List.of(HASH, DIRECTORY),
            false),
    KNOWN_DEFECT(
            "knownDefect",
            List.of(ENTRY_ID, POSITION, SIGNATURE),
            List.of(ENTRY_ID, POSITION),
            List.of(),
            true),
    STAGE("stage", stageColumns(), List.of(ENTRY_ID, STAGE_NAME), List.of(), true),
    EXPR(
            "expr",
            List.of(ENTRY_ID, NAME, OCCURRENCES),
            List.of(ENTRY_ID, NAME),
            List.of(),
            true),
    UNREADABLE(
            "unreadable", List.of(DIRECTORY, HASH, ERROR), List.of(DIRECTORY, HASH), List.of(), false);

    private final String tableName;
    private final List<Column> columns;
    private final List<Column> primaryKey;
    private final List<Column> unique;
    private final boolean referencesEntry;

    DatabaseTable(
            String tableName,
            List<Column> columns,
            List<Column> primaryKey,
            List<Column> unique,
            boolean referencesEntry) {
        this.tableName = tableName;
        this.columns = List.copyOf(columns);
        this.primaryKey = List.copyOf(primaryKey);
        this.unique = List.copyOf(unique);
        this.referencesEntry = referencesEntry;
    }

    String tableName() {
        return tableName;
    }

    List<Column> columns() {
        return columns;
    }

    String createStatement() {
        var definitions = new ArrayList<String>();
        columns.forEach(column -> definitions.add(column.definition()));
        definitions.add("PRIMARY KEY (" + names(primaryKey) + ")");
        if (!unique.isEmpty()) {
            definitions.add("UNIQUE (" + names(unique) + ")");
        }
        if (referencesEntry) {
            definitions.add("FOREIGN KEY (" + ENTRY_ID.name() + ") REFERENCES "
                    + ENTRY.tableName + " (" + ID.name() + ")");
        }
        // A table keyed by anything but its integer row id stores its rows in the key's B-tree.
        var rowid = primaryKey.equals(List.of(ID)) ? "" : " WITHOUT ROWID";
        return "CREATE TABLE " + tableName + " (" + String.join(", ", definitions) + ")" + rowid;
    }

    String insertStatement() {
        return "INSERT INTO " + tableName + " (" + names(columns) + ") VALUES ("
                + columns.stream().map(column -> "?").collect(Collectors.joining(", ")) + ")";
    }

    private static String names(List<Column> columns) {
        return columns.stream().map(Column::name).collect(Collectors.joining(", "));
    }

    private static List<Column> stageColumns() {
        var columns = new ArrayList<>(List.of(
                ENTRY_ID,
                STAGE_NAME,
                VERDICT,
                START_TIME,
                END_TIME,
                DURATION_MILLIS,
                CODE,
                DETAIL,
                PHASE,
                SATURATED));
        columns.addAll(METRICS.values());
        return columns;
    }
}
