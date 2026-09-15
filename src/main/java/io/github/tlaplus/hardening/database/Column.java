package io.github.tlaplus.hardening.database;

import java.util.Objects;

/** A column of a database table. */
record Column(String name, SqlType type, boolean nullable) {
    Column {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    static Column required(String name, SqlType type) {
        return new Column(name, type, false);
    }

    static Column optional(String name, SqlType type) {
        return new Column(name, type, true);
    }

    /** Returns the column definition of a {@code CREATE TABLE} statement. */
    String definition() {
        return name + " " + type.name() + (nullable ? "" : " NOT NULL");
    }
}
