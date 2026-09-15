package io.github.tlaplus.hardening.database;

import static io.github.tlaplus.hardening.database.DatabaseColumns.KEY;
import static io.github.tlaplus.hardening.database.DatabaseColumns.VALUE;

import io.github.tlaplus.hardening.common.Cleanup;
import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exports a corpus to a new SQLite database, as ADR 0009 defines it.
 *
 * <p>The export writes a temporary file beside the output and moves it onto the output only after
 * the transaction commits, so a failed or interrupted export leaves no partial database. It never
 * writes to the corpus. {@link EntryBatchExporter} reads, replays and inserts the entries.
 */
public final class CorpusExport {
    /**
     * What to export and where.
     *
     * @param corpus the corpus root
     * @param output the database file
     * @param replace whether an existing output may be replaced
     * @param lock whether to hold the corpus lock, which makes the export a snapshot
     * @param provenance what the {@code export} table records
     */
    public record Options(
            Path corpus, Path output, boolean replace, boolean lock, Provenance provenance) {
        public Options {
            Objects.requireNonNull(corpus, "corpus");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    /**
     * What the {@code export} table records about the export itself.
     *
     * @param fuzztlaVersion the {@code fuzztla --version} string
     * @param exportedAt when the export started
     */
    public record Provenance(String fuzztlaVersion, Instant exportedAt) {
        public Provenance {
            Objects.requireNonNull(fuzztlaVersion, "fuzztlaVersion");
            Objects.requireNonNull(exportedAt, "exportedAt");
        }
    }

    /**
     * How inputs are replayed.
     *
     * @param analysis derives the features of one input
     * @param threads the threads that call {@code analysis} concurrently
     */
    public record Analysis(InputAnalysis analysis, int threads) {
        public Analysis {
            Objects.requireNonNull(analysis, "analysis");
            Preconditions.requirePositive(threads, "threads");
        }
    }

    /**
     * What an export wrote.
     *
     * @param output the database file
     * @param entries entry files exported as rows of {@code entry}
     * @param unreadable entry files that did not decode, exported as rows of {@code unreadable}
     * @param replayFailures exported entries whose input could not be replayed
     * @param vanished entry files that disappeared between listing and reading, without the lock
     */
    public record Summary(
            Path output, long entries, long unreadable, long replayFailures, long vanished) {}

    private CorpusExport() {}

    /** The default output: {@code corpus.sqlite} in the corpus root. */
    public static Path defaultOutput(Path corpus) {
        return corpus.resolve("corpus.sqlite");
    }

    public static Summary run(Options options, Analysis analysis)
            throws IOException, CorpusException, CorpusDatabaseException, InterruptedException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(analysis, "analysis");
        var corpus = CorpusDirectory.openExisting(options.corpus());
        var output = options.output().toAbsolutePath().normalize();
        if (!options.replace() && Files.exists(output)) {
            throw new CorpusDatabaseException("database already exists: " + options.output());
        }
        try (var lock = options.lock() ? corpus.acquireExclusiveLock() : null) {
            var temporary = Files.createTempFile(
                    output.getParent(), "." + output.getFileName() + "-", ".tmp");
            try {
                var summary = write(corpus, temporary, options, analysis);
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
                    | InterruptedException
                    | RuntimeException
                    | Error exception) {
                Cleanup.suppressIOException(exception, () -> Files.deleteIfExists(temporary));
                throw exception;
            }
        }
    }

    private static Summary write(
            CorpusDirectory corpus, Path file, Options options, Analysis analysis)
            throws IOException, CorpusException, CorpusDatabaseException, InterruptedException {
        try (var writer = CorpusDatabaseWriter.create(file);
                var entries = new EntryBatchExporter(writer, analysis, !options.lock())) {
            for (var property : properties(corpus, options.provenance()).entrySet()) {
                writer.insert(new Row(DatabaseTable.EXPORT)
                        .set(KEY, property.getKey())
                        .set(VALUE, property.getValue()));
            }
            entries.export(corpus.storedEntries());
            writer.commit();
            return entries.summary(options.output());
        } catch (SQLException exception) {
            throw new CorpusDatabaseException(
                    "cannot write database '"
                            + options.output()
                            + "': "
                            + Diagnostics.message(exception),
                    exception);
        }
    }

    private static Map<String, String> properties(CorpusDirectory corpus, Provenance provenance) {
        var properties = new LinkedHashMap<String, String>();
        properties.put("corpus", corpus.resolve(CorpusPath.ROOT).toString());
        properties.put("exportedAt", Row.timestamp(provenance.exportedAt()));
        properties.put("fuzztlaVersion", provenance.fuzztlaVersion());
        return properties;
    }
}
