package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * Replay policy for one corpus record: a run may proceed only when the record says what the run
 * would write. A corpus without the record reads as {@code unrecorded}, the value every corpus had
 * before the record existed. Any other value is written only on the first run into an empty corpus.
 *
 * @param mismatch the diagnostic for a saved value (or the encoded {@code unrecorded}) and an
 *     expected value that differ, both encoded
 */
public record ReplayRecord<T>(
        CorpusRecord record, T unrecorded, Codec<T> codec, BinaryOperator<String> mismatch) {
    /** How a record's value is written as text; decoding fails for text no build wrote. */
    public record Codec<T>(Function<T, String> encode, Function<String, Optional<T>> decode) {
        /** Stores text as it is. */
        public static final Codec<String> TEXT = new Codec<>(Function.identity(), Optional::of);

        public Codec {
            Objects.requireNonNull(encode, "encode");
            Objects.requireNonNull(decode, "decode");
        }
    }

    public ReplayRecord {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(unrecorded, "unrecorded");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(mismatch, "mismatch");
    }

    /**
     * Checks {@code expected} against the saved record. The writer calls this under the corpus lock
     * with {@code initialize} before decoding or admitting any entry; readers pass false.
     */
    public void verify(CorpusDirectory corpus, T expected, boolean initialize)
            throws IOException, CorpusException, WorkflowException {
        Objects.requireNonNull(corpus, "corpus");
        var encoded = codec.encode().apply(Objects.requireNonNull(expected, "expected"));
        var saved = savedText(corpus);
        if (saved.isPresent()) {
            if (!saved.get().equals(encoded)) {
                throw new WorkflowException(mismatch.apply(saved.get(), encoded));
            }
        } else if (!expected.equals(unrecorded)) {
            if (!initialize || corpus.hasStoredInputs()) {
                throw new WorkflowException(mismatch.apply(codec.encode().apply(unrecorded), encoded));
            }
            corpus.writeRecord(record, encoded.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Returns the saved value, or {@code unrecorded} for a corpus without the record. */
    public T read(CorpusDirectory corpus) throws IOException, CorpusException, WorkflowException {
        var saved = savedText(corpus);
        if (saved.isEmpty()) {
            return unrecorded;
        }
        return codec.decode().apply(saved.get()).orElseThrow(() -> new WorkflowException(
                "unreadable " + record.description() + " '" + saved.get() + "'"));
    }

    private Optional<String> savedText(CorpusDirectory corpus) throws IOException, CorpusException {
        return corpus.readRecord(record).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }
}
