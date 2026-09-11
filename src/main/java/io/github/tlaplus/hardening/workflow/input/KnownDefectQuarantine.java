package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.StoreResult;
import io.github.tlaplus.hardening.gen.InputKind;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps at most a fixed number of quarantined candidates per primary known-defect signature in
 * {@code 00-known-defects}; past the cap, a match is counted by the caller and discarded here.
 *
 * <p>The caps continue from what earlier runs stored. Generator workers share one instance: a
 * worker reserves a slot before it writes, so concurrent matches cannot overfill a signature.
 */
public final class KnownDefectQuarantine {
    /** What became of one known-defect candidate. */
    enum Outcome {
        STORED,
        DUPLICATE,
        DISCARDED
    }

    private final CorpusDirectory corpus;
    private final int samplesPerSignature;
    private final ConcurrentHashMap<String, AtomicLong> stored = new ConcurrentHashMap<>();

    private KnownDefectQuarantine(
            CorpusDirectory corpus, int samplesPerSignature, Map<String, Long> alreadyStored) {
        Preconditions.requireNonnegative(samplesPerSignature, "samplesPerSignature");
        Preconditions.require(corpus != null || samplesPerSignature == 0,
                "a quarantine that stores samples needs a corpus");
        this.corpus = corpus;
        this.samplesPerSignature = samplesPerSignature;
        alreadyStored.forEach((signature, count) -> stored.put(signature, new AtomicLong(count)));
    }

    /** Opens the quarantine of a corpus, counting the samples earlier runs kept. */
    public static KnownDefectQuarantine open(CorpusDirectory corpus, int samplesPerSignature)
            throws IOException, CorpusException {
        Objects.requireNonNull(corpus, "corpus");
        return new KnownDefectQuarantine(corpus, samplesPerSignature, corpus.knownDefectSamples());
    }

    /** Returns a quarantine that keeps nothing, for admission without a database. */
    static KnownDefectQuarantine discarding() {
        return new KnownDefectQuarantine(null, 0, Map.of());
    }

    Outcome record(InputKind kind, byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        var primary = generation.primaryKnownDefect().orElseThrow(() -> new IllegalArgumentException(
                "a quarantined candidate must name the known-defect signatures it matched"));
        var count = stored.computeIfAbsent(primary, _ -> new AtomicLong());
        if (count.getAndUpdate(reserved -> reserved < samplesPerSignature ? reserved + 1 : reserved)
                >= samplesPerSignature) {
            return Outcome.DISCARDED;
        }
        if (corpus.quarantine(kind, input, generation) == StoreResult.DUPLICATE) {
            count.decrementAndGet();
            return Outcome.DUPLICATE;
        }
        return Outcome.STORED;
    }
}
