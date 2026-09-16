package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusFormatException;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * The entries of {@code 04quality-pass} that a generation mutates: those of the kind the run
 * generates, read once when the generation starts. Workers share one pool.
 */
public final class ParentPool {
    private final List<Parent> parents;

    private ParentPool(List<Parent> parents) {
        this.parents = List.copyOf(parents);
    }

    /** Reads the parents of {@code kind} from the corpus, which the caller holds locked. */
    public static ParentPool load(CorpusDirectory corpus, InputKind kind, Generator<SpecArtifact> decoder)
            throws IOException, CorpusException {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(decoder, "decoder");
        var parents = new ArrayList<Parent>();
        for (var stored : corpus.resultEntries(CorpusStage.QUALITY, CorpusVerdict.PASS)) {
            final CorpusEnvelope envelope;
            try {
                envelope = CorpusEnvelopeCodec.decodeEnvelope(stored.read());
            } catch (CorpusFormatException exception) {
                throw new CorpusException(
                        "invalid parent entry '" + stored.path() + "': " + Diagnostics.message(exception),
                        exception);
            }
            if (envelope.corpusInput().kind() == kind) {
                parents.add(new Parent(
                        stored.digest(),
                        envelope.corpusInput().input(),
                        envelope.generation().map(GenerationMetadata::cohort).orElse(0),
                        decoder));
            }
        }
        return new ParentPool(parents);
    }

    public boolean isEmpty() {
        return parents.isEmpty();
    }

    public int size() {
        return parents.size();
    }

    /** Draws a parent uniformly. */
    Parent draw(RandomGenerator random) {
        if (parents.isEmpty()) {
            throw new IllegalStateException("the parent pool is empty");
        }
        return parents.get(random.nextInt(parents.size()));
    }

    /** One selected entry that mutants are derived from. */
    static final class Parent {
        private final String digest;
        private final byte[] input;
        private final int cohort;
        private final Generator<SpecArtifact> decoder;
        private volatile String module;

        private Parent(String digest, byte[] input, int cohort, Generator<SpecArtifact> decoder) {
            this.digest = digest;
            this.input = input;
            this.cohort = cohort;
            this.decoder = decoder;
        }

        String digest() {
            return digest;
        }

        /** Returns the parent's bytes; the caller must not modify them. */
        byte[] input() {
            return input;
        }

        int cohort() {
            return cohort;
        }

        /**
         * Reports whether {@code candidate} renders to this parent's module. The parent is decoded
         * and rendered on first use only.
         */
        boolean rendersAs(SpecArtifact candidate) {
            var rendered = module;
            if (rendered == null) {
                rendered = SpecText.render(decoder.generate(input).module());
                module = rendered;
            }
            return rendered.equals(SpecText.render(candidate.module()));
        }
    }
}
