package io.github.tlaplus.hardening.database;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The values of one row, set by column. A column checks the type of its value, so a row cannot bind
 * a value to the wrong parameter or store a value of the wrong type.
 */
final class Row {
    /** ISO-8601 in UTC with exactly three fraction digits, so text order is chronological. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final DatabaseTable table;
    private final Map<Column, Object> values = new HashMap<>();

    Row(DatabaseTable table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    DatabaseTable table() {
        return table;
    }

    Row set(Column column, long value) {
        return put(column, value);
    }

    Row set(Column column, double value) {
        return put(column, value);
    }

    Row set(Column column, String value) {
        return put(column, Objects.requireNonNull(value, "value"));
    }

    Row set(Column column, Instant value) {
        return put(column, timestamp(Objects.requireNonNull(value, "value")));
    }

    /** Returns the value of {@code column}, or {@code null} when the row does not set it. */
    Object value(Column column) {
        requireColumn(column);
        var value = values.get(column);
        if (value == null && !column.nullable()) {
            throw new IllegalStateException(describe(column) + " requires a value");
        }
        return value;
    }

    /** Formats a timestamp the way every timestamp column stores it. */
    static String timestamp(Instant value) {
        return TIMESTAMP.format(value);
    }

    private Row put(Column column, Object value) {
        requireColumn(column);
        if (!column.type().accepts(value)) {
            throw new IllegalArgumentException(
                    describe(column) + " does not store " + value.getClass().getSimpleName());
        }
        if (values.putIfAbsent(column, value) != null) {
            throw new IllegalArgumentException(describe(column) + " is already set");
        }
        return this;
    }

    private void requireColumn(Column column) {
        if (!table.columns().contains(Objects.requireNonNull(column, "column"))) {
            throw new IllegalArgumentException(describe(column) + " is not in the table");
        }
    }

    private String describe(Column column) {
        return "column " + table.tableName() + "." + column.name();
    }
}
