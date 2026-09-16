package io.github.tlaplus.hardening.database;

import static io.github.tlaplus.hardening.database.Column.optional;
import static io.github.tlaplus.hardening.database.Column.required;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The columns of the database tables. A column that stores an envelope field has that field's name,
 * and a column shared by two tables is one constant.
 */
final class DatabaseColumns {
    static final Column KEY = required("key", SqlType.TEXT);
    static final Column VALUE = required("value", SqlType.TEXT);

    static final Column ID = required("id", SqlType.INTEGER);
    static final Column DIRECTORY = required("directory", SqlType.TEXT);
    static final Column HASH = required("hash", SqlType.TEXT);
    static final Column KIND = required("kind", SqlType.TEXT);
    static final Column INPUT_BYTES = required("inputBytes", SqlType.INTEGER);
    static final Column COHORT = optional("cohort", SqlType.INTEGER);
    static final Column RICHNESS = optional("richness", SqlType.REAL);
    static final Column GENERATION = optional("generation", SqlType.INTEGER);
    static final Column PARENT = optional("parent", SqlType.TEXT);
    static final Column EVALUATED_NODES = optional("evaluatedNodes", SqlType.INTEGER);
    static final Column REPLAY_ERROR = optional("replayError", SqlType.TEXT);

    static final Column ENTRY_ID = required("entryId", SqlType.INTEGER);
    static final Column POSITION = required("position", SqlType.INTEGER);
    static final Column SIGNATURE = required("signature", SqlType.TEXT);
    static final Column OPERATOR = required("operator", SqlType.TEXT);

    static final Column STAGE_NAME = required("stage", SqlType.TEXT);
    static final Column VERDICT = required("verdict", SqlType.TEXT);
    static final Column START_TIME = required("startTime", SqlType.TEXT);
    static final Column END_TIME = required("endTime", SqlType.TEXT);
    static final Column DURATION_MILLIS = required("durationMillis", SqlType.INTEGER);
    static final Column CODE = optional("code", SqlType.INTEGER);
    static final Column DETAIL = optional("detail", SqlType.TEXT);
    static final Column PHASE = optional("phase", SqlType.TEXT);
    static final Column SATURATED = optional("saturated", SqlType.INTEGER);
    /** One column per exploration count, named by its field name, in declaration order. */
    static final Map<ExplorationCount, Column> METRICS = metricColumns();

    static final Column NAME = required("name", SqlType.TEXT);
    static final Column OCCURRENCES = required("occurrences", SqlType.INTEGER);

    static final Column ERROR = required("error", SqlType.TEXT);

    private DatabaseColumns() {}

    private static Map<ExplorationCount, Column> metricColumns() {
        var columns = new EnumMap<ExplorationCount, Column>(ExplorationCount.class);
        for (var count : ExplorationCount.values()) {
            columns.put(count, optional(count.fieldName(), SqlType.INTEGER));
        }
        return Collections.unmodifiableMap(columns);
    }
}
