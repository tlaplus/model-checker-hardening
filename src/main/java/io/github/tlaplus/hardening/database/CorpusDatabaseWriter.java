package io.github.tlaplus.hardening.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the rows of one export into a new SQLite file, in a single transaction.
 *
 * <p>The file is a private temporary file that is discarded when the export fails, so the
 * connection turns off the rollback journal and synchronous writes: there is nothing to recover.
 * Inserts are batched per table. {@link #commit()} creates the indexes and views, sets the schema
 * version and commits; closing without committing discards the rows.
 */
final class CorpusDatabaseWriter implements AutoCloseable {
    private static final int BATCH_SIZE = 2_000;

    private final Connection connection;
    private final Map<DatabaseTable, PreparedStatement> inserts = new EnumMap<>(DatabaseTable.class);
    private final Map<DatabaseTable, Integer> pending = new EnumMap<>(DatabaseTable.class);

    private CorpusDatabaseWriter(Connection connection) {
        this.connection = connection;
    }

    /** Opens {@code file}, which must be absent or empty, and creates the tables. */
    static CorpusDatabaseWriter create(Path file) throws SQLException {
        Objects.requireNonNull(file, "file");
        var connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        var writer = new CorpusDatabaseWriter(connection);
        try {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = OFF");
                statement.execute("PRAGMA synchronous = OFF");
            }
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                for (var sql : CorpusDatabaseSchema.tableStatements()) {
                    statement.execute(sql);
                }
            }
            for (var table : DatabaseTable.values()) {
                writer.inserts.put(table, connection.prepareStatement(table.insertStatement()));
                writer.pending.put(table, 0);
            }
            return writer;
        } catch (SQLException | RuntimeException exception) {
            try {
                writer.close();
            } catch (SQLException closing) {
                exception.addSuppressed(closing);
            }
            throw exception;
        }
    }

    void insert(Row row) throws SQLException {
        var table = row.table();
        var statement = inserts.get(table);
        var columns = table.columns();
        for (var index = 0; index < columns.size(); index++) {
            var column = columns.get(index);
            column.type().bind(statement, index + 1, row.value(column));
        }
        statement.addBatch();
        var count = pending.merge(table, 1, Integer::sum);
        if (count >= BATCH_SIZE) {
            flush(table);
        }
    }

    /** Writes the pending rows, creates the indexes and views, and commits. */
    void commit() throws SQLException {
        for (var table : DatabaseTable.values()) {
            flush(table);
        }
        try (var statement = connection.createStatement()) {
            for (var sql : CorpusDatabaseSchema.finishingStatements()) {
                statement.execute(sql);
            }
        }
        connection.commit();
    }

    @Override
    public void close() throws SQLException {
        SQLException failure = null;
        for (var statement : inserts.values()) {
            try {
                statement.close();
            } catch (SQLException exception) {
                failure = accumulate(failure, exception);
            }
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            failure = accumulate(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void flush(DatabaseTable table) throws SQLException {
        if (pending.get(table) > 0) {
            inserts.get(table).executeBatch();
            pending.put(table, 0);
        }
    }

    private static SQLException accumulate(SQLException failure, SQLException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }
}
