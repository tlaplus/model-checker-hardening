package io.github.tlaplus.hardening.database;

import static io.github.tlaplus.hardening.database.DatabaseColumns.KEY;
import static io.github.tlaplus.hardening.database.DatabaseColumns.VALUE;

import io.github.tlaplus.hardening.common.Cleanup;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusFormatException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Exports a corpus to a new SQLite database, as ADR 0009 defines it.
 *
 * <p>The export writes a temporary file beside the output and moves it onto the output only after
 * the transaction commits, so a failed export leaves no partial database. It never writes to the
 * corpus.
 */
public final class CorpusExport {
    /**
     * What to export and how.
     *
     * @param corpus the corpus root
     * @param output the database file
     * @param replace whether an existing output may be replaced
     * @param lock whether to hold the corpus lock, which makes the export a snapshot
     * @param fuzztlaVersion the version string recorded in the {@code export} table
     * @param exportedAt the time recorded in the {@code export} table
     */
    public record Options(
            Path corpus,
            Path output,
            boolean replace,
            boolean lock,
            String fuzztlaVersion,
            Instant exportedAt) {
        public Options {
            Objects.requireNonNull(corpus, "corpus");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(fuzztlaVersion, "fuzztlaVersion");
            Objects.requireNonNull(exportedAt, "exportedAt");
        }
    }

    /**
     * What an export wrote.
     *
     * @param output the database file
     * @param entries entry files exported as rows of {@code entry}
     * @param unreadable entry files that did not decode, exported as rows of {@code unreadable}
     * @param vanished entry files that disappeared between listing and reading, without the lock
     */
    public record Summary(Path output, long entries, long unreadable, long vanished) {}

    private CorpusExport() {}

    /** The default output: {@code corpus.sqlite} in the corpus root. */
    public static Path defaultOutput(Path corpus) {
        return corpus.resolve("corpus.sqlite");
    }

    public static Summary run(Options options)
            throws IOException, CorpusException, CorpusDatabaseException {
        Objects.requireNonNull(options, "options");
        var corpus = CorpusDirectory.openExisting(options.corpus());
        var output = options.output().toAbsolutePath().normalize();
        if (!options.replace() && Files.exists(output)) {
            throw new CorpusDatabaseException("database already exists: " + options.output());
        }
        try (var lock = options.lock() ? corpus.acquireExclusiveLock() : null) {
            var temporary = Files.createTempFile(
                    output.getParent(), "." + output.getFileName() + "-", ".tmp");
            try {
                var summary = write(corpus, temporary, options);
                if (options.replace()) {
                    Files.move(temporary, output,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
                }
                return summary;
            } catch (IOException
                    | CorpusException
                    | CorpusDatabaseException
                    | RuntimeException exception) {
                Cleanup.suppressIOException(exception, () -> Files.deleteIfExists(temporary));
                throw exception;
            }
        }
    }

    private static Summary write(CorpusDirectory corpus, Path file, Options options)
            throws IOException, CorpusException, CorpusDatabaseException {
        long entries = 0;
        long unreadable = 0;
        long vanished = 0;
        try (var writer = CorpusDatabaseWriter.create(file)) {
            for (var property : properties(corpus, options).entrySet()) {
                writer.insert(new Row(DatabaseTable.EXPORT)
                        .set(KEY, property.getKey())
                        .set(VALUE, property.getValue()));
            }
            for (var stored : corpus.storedEntries()) {
                final byte[] encoded;
                try {
                    encoded = Files.readAllBytes(stored.path());
                } catch (NoSuchFileException exception) {
                    if (options.lock()) {
                        throw exception;
                    }
                    vanished++;
                    continue;
                }
                try {
                    var envelope = CorpusEnvelopeCodec.decodeEnvelope(encoded);
                    entries++;
                    for (var row : EntryRows.of(entries, stored, envelope).all()) {
                        writer.insert(row);
                    }
                } catch (CorpusFormatException exception) {
                    unreadable++;
                    writer.insert(EntryRows.unreadable(stored, Diagnostics.message(exception)));
                }
            }
            writer.commit();
        } catch (SQLException exception) {
            throw new CorpusDatabaseException(
                    "cannot write database '"
                            + options.output()
                            + "': "
                            + Diagnostics.message(exception),
                    exception);
        }
        return new Summary(options.output(), entries, unreadable, vanished);
    }

    private static LinkedHashMap<String, String> properties(CorpusDirectory corpus, Options options) {
        var properties = new LinkedHashMap<String, String>();
        properties.put("corpus", corpus.resolve(CorpusPath.ROOT).toString());
        properties.put("exportedAt", Row.timestamp(options.exportedAt()));
        properties.put("fuzztlaVersion", options.fuzztlaVersion());
        return properties;
    }
}
