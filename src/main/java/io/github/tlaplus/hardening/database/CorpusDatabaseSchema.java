package io.github.tlaplus.hardening.database;

import static io.github.tlaplus.hardening.database.DatabaseColumns.CODE;
import static io.github.tlaplus.hardening.database.DatabaseColumns.COHORT;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ENTRY_ID;
import static io.github.tlaplus.hardening.database.DatabaseColumns.HASH;
import static io.github.tlaplus.hardening.database.DatabaseColumns.ID;
import static io.github.tlaplus.hardening.database.DatabaseColumns.METRICS;
import static io.github.tlaplus.hardening.database.DatabaseColumns.STAGE_NAME;
import static io.github.tlaplus.hardening.database.DatabaseColumns.VERDICT;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The schema of the corpus database, as the corpus-database manual documents it.
 *
 * <p>Tables and the stage-record columns derive from {@link DatabaseTable}; the {@code verdictPair}
 * view derives from {@link CorpusStage#checkerBranches()}. Any change to a table, column or view
 * increments {@link #VERSION} and updates the manual.
 */
final class CorpusDatabaseSchema {
    /** Stored in {@code PRAGMA user_version}. */
    static final int VERSION = 3;

    static final String VERDICT_PAIR_VIEW = "verdictPair";

    private static final String ENTRY_ALIAS = "e";
    private static final String AGGREGATOR_ALIAS = "a";

    private CorpusDatabaseSchema() {}

    /** Statements that create the tables, before any row is inserted. */
    static List<String> tableStatements() {
        var statements = new ArrayList<String>();
        for (var table : DatabaseTable.values()) {
            statements.add(table.createStatement());
        }
        return statements;
    }

    /** Statements that create the indexes and views and set the version, after the rows. */
    static List<String> finishingStatements() {
        return List.of(
                index(DatabaseTable.STAGE, STAGE_NAME, VERDICT),
                verdictPairView(),
                "PRAGMA user_version = " + VERSION);
    }

    /** One row per aggregated entry, with each checker's verdict, failure code and trace length. */
    static String verdictPairView() {
        var entry = DatabaseTable.ENTRY.tableName();
        var stage = DatabaseTable.STAGE.tableName();
        var traceLength = METRICS.get(ExplorationCount.TRACE_LENGTH);
        var select = new ArrayList<String>(List.of(
                qualified(ENTRY_ALIAS, ID) + " AS " + ENTRY_ID.name(),
                qualified(ENTRY_ALIAS, HASH) + " AS " + HASH.name(),
                qualified(ENTRY_ALIAS, COHORT) + " AS " + COHORT.name(),
                qualified(AGGREGATOR_ALIAS, VERDICT) + " AS " + CorpusStage.AGGREGATOR.metadataName()));
        var joins = new StringBuilder(" JOIN " + stage + " " + AGGREGATOR_ALIAS
                + stageJoin(AGGREGATOR_ALIAS, CorpusStage.AGGREGATOR));
        for (var checker : CorpusStage.checkerBranches()) {
            var alias = checker.metadataName();
            select.add(qualified(alias, VERDICT) + " AS " + alias);
            select.add(qualified(alias, CODE) + " AS " + alias + "Code");
            select.add(qualified(alias, traceLength) + " AS " + alias + capitalized(traceLength.name()));
            joins.append(" JOIN ").append(stage).append(' ').append(alias)
                    .append(stageJoin(alias, checker));
        }
        return "CREATE VIEW " + VERDICT_PAIR_VIEW + " AS SELECT " + String.join(", ", select)
                + " FROM " + entry + " " + ENTRY_ALIAS + joins;
    }

    private static String stageJoin(String alias, CorpusStage stage) {
        return " ON " + qualified(alias, ENTRY_ID) + " = " + qualified(ENTRY_ALIAS, ID)
                + " AND " + qualified(alias, STAGE_NAME) + " = '" + stage.metadataName() + "'";
    }

    private static String index(DatabaseTable table, Column... columns) {
        var names = Arrays.stream(columns).map(Column::name).toList();
        var indexName = table.tableName() + "By"
                + names.stream().map(CorpusDatabaseSchema::capitalized).collect(Collectors.joining("And"));
        return "CREATE INDEX " + indexName + " ON " + table.tableName()
                + " (" + String.join(", ", names) + ")";
    }

    private static String qualified(String alias, Column column) {
        return alias + "." + column.name();
    }

    private static String capitalized(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
