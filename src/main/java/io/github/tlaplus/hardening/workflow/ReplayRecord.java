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
 * A corpus record written before ADR 0025, which no run writes any more: a run may proceed only
 * when the configuration repeats what the record says. A corpus without the record reads as {@code
 * unrecorded}.
 *
 * @param mismatch the diagnostic for a saved value and a configured value that differ, both encoded
 */
public record ReplayRecord<T>(
        CorpusRecord record, T unrecorded, Codec<T> codec, BinaryOperator<String> mismatch) {
    /** How a record's value is written as text; decoding fails for text no build wrote. */
    public record Codec<T>(Function<T, String> encode, Function<String, Optional<T>> decode) {
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
     * Refuses a saved record that differs from {@code expected}, the configured value; a corpus
     * without the record passes, since its configuration now says what the record said.
     */
    public void requireUnchanged(CorpusDirectory corpus, T expected)
            throws IOException, CorpusException, WorkflowException {
        var encoded = codec.encode().apply(Objects.requireNonNull(expected, "expected"));
        var saved = savedText(Objects.requireNonNull(corpus, "corpus"));
        if (saved.isPresent() && !saved.get().equals(encoded)) {
            throw new WorkflowException(mismatch.apply(saved.get(), encoded));
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
