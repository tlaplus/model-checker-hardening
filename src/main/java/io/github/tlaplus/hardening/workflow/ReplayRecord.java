package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * Replay policy for one corpus record: a run may proceed only when the record says what the run
 * would write. A corpus without the record reads as {@code unrecorded}, the value every corpus had
 * before the record existed. Any other value is written only on the first run into an empty corpus.
 *
 * @param mismatch the diagnostic for a saved value (or {@code unrecorded}) and an expected value
 *     that differ
 */
public record ReplayRecord(CorpusRecord record, String unrecorded, BinaryOperator<String> mismatch) {
    public ReplayRecord {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(unrecorded, "unrecorded");
        Objects.requireNonNull(mismatch, "mismatch");
    }

    /**
     * Checks {@code expected} against the saved record. The writer calls this under the corpus lock
     * with {@code initialize} before decoding or admitting any entry; readers pass false.
     */
    public void verify(CorpusDirectory corpus, String expected, boolean initialize)
            throws IOException, CorpusException, WorkflowException {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(expected, "expected");
        var saved = corpus.readRecord(record).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
        if (saved.isPresent()) {
            if (!saved.get().equals(expected)) {
                throw new WorkflowException(mismatch.apply(saved.get(), expected));
            }
        } else if (!expected.equals(unrecorded)) {
            if (!initialize || corpus.hasStoredInputs()) {
                throw new WorkflowException(mismatch.apply(unrecorded, expected));
            }
            corpus.writeRecord(record, expected.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Returns the saved value, or {@code unrecorded} for a corpus without the record. */
    public String read(CorpusDirectory corpus) throws IOException, CorpusException {
        return corpus.readRecord(record)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElse(unrecorded);
    }
}
