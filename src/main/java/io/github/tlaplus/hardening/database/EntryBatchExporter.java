package io.github.tlaplus.hardening.database;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusFormatException;
import io.github.tlaplus.hardening.corpus.StoredEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Exports listed entry files in chunks. For each chunk it reads and decodes the envelopes on the
 * calling thread, replays the inputs on a fixed pool, and inserts the rows in listing order on the
 * calling thread. Row ids and contents therefore do not depend on the number of threads, and only
 * the calling thread uses the database.
 */
final class EntryBatchExporter implements AutoCloseable {
    static final int CHUNK_SIZE = 1_024;

    private final CorpusDatabaseWriter writer;
    private final CorpusExport.Analysis analysis;
    private final boolean tolerateVanished;
    private final ExecutorService replays;

    private long entries;
    private long unreadable;
    private long replayFailures;
    private long vanished;

    /**
     * @param tolerateVanished whether an entry that disappears after listing is skipped rather than
     *     reported, as it may when no lock is held
     */
    EntryBatchExporter(
            CorpusDatabaseWriter writer, CorpusExport.Analysis analysis, boolean tolerateVanished) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.analysis = Objects.requireNonNull(analysis, "analysis");
        this.tolerateVanished = tolerateVanished;
        replays = Executors.newFixedThreadPool(
                analysis.threads(), Thread.ofPlatform().name("fuzztla-export-", 0).daemon().factory());
    }

    void export(List<StoredEntry> listing)
            throws IOException, SQLException, InterruptedException {
        for (var start = 0; start < listing.size(); start += CHUNK_SIZE) {
            exportChunk(listing.subList(start, Math.min(listing.size(), start + CHUNK_SIZE)));
        }
    }

    CorpusExport.Summary summary(Path output) {
        return new CorpusExport.Summary(output, entries, unreadable, replayFailures, vanished);
    }

    /** Cancels replays that have not started; a running replay finishes on its daemon thread. */
    @Override
    public void close() {
        replays.shutdownNow();
    }

    private void exportChunk(List<StoredEntry> chunk)
            throws IOException, SQLException, InterruptedException {
        var decoded = new ArrayList<Decoded>(chunk.size());
        for (var stored : chunk) {
            final byte[] encoded;
            try {
                encoded = Files.readAllBytes(stored.path());
            } catch (NoSuchFileException exception) {
                if (!tolerateVanished) {
                    throw exception;
                }
                vanished++;
                continue;
            }
            try {
                decoded.add(new Decoded(stored, CorpusEnvelopeCodec.decodeEnvelope(encoded)));
            } catch (CorpusFormatException exception) {
                unreadable++;
                writer.insert(EntryRows.unreadable(stored, Diagnostics.message(exception)));
            }
        }

        var tasks = new ArrayList<Callable<ReplayOutcome>>(decoded.size());
        for (var entry : decoded) {
            var input = entry.envelope().corpusInput();
            tasks.add(() -> ReplayOutcome.of(analysis.analysis(), input));
        }
        var outcomes = replays.invokeAll(tasks);

        for (var index = 0; index < decoded.size(); index++) {
            var entry = decoded.get(index);
            var outcome = result(outcomes.get(index));
            entries++;
            if (outcome instanceof ReplayOutcome.Failed) {
                replayFailures++;
            }
            for (var row : EntryRows.of(entries, entry.stored(), entry.envelope(), outcome).all()) {
                writer.insert(row);
            }
        }
    }

    /** {@link ReplayOutcome#of} contains every failure of an input, so only a JVM error remains. */
    private static ReplayOutcome result(Future<ReplayOutcome> outcome) throws InterruptedException {
        try {
            return outcome.get();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("input replay failed", exception.getCause());
        }
    }

    private record Decoded(StoredEntry stored, CorpusEnvelope envelope) {}
}
