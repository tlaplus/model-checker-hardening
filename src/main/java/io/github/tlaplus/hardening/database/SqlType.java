package io.github.tlaplus.hardening.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/** A SQLite storage class, with the Java type a row stores for it. */
enum SqlType {
    INTEGER(Long.class, Types.BIGINT),
    REAL(Double.class, Types.DOUBLE),
    TEXT(String.class, Types.VARCHAR);

    private final Class<?> javaType;
    private final int jdbcType;

    SqlType(Class<?> javaType, int jdbcType) {
        this.javaType = javaType;
        this.jdbcType = jdbcType;
    }

    /** Whether a row may store {@code value} in a column of this type. */
    boolean accepts(Object value) {
        return javaType.isInstance(value);
    }

    /** Binds {@code value}, or SQL {@code NULL} when it is {@code null}, to one parameter. */
    void bind(PreparedStatement statement, int parameter, Object value) throws SQLException {
        if (value == null) {
            statement.setNull(parameter, jdbcType);
        } else {
            statement.setObject(parameter, value, jdbcType);
        }
    }
}
