package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * How a candidate source lays out a drawn payload for the decoders of a technique. A random byte
 * array read as a metamorphic input would start with a random base length, which almost always
 * exceeds the input: the base would take every byte, no rule would apply, and the decoder would
 * reject the candidate (ADR 0016 §6).
 */
public enum CandidateLayout {
    /** The payload is the input; no randomness is drawn, so a conformance run's stream is unchanged. */
    CONFORMANCE {
        @Override
        public byte[] encode(byte[] payload, RandomGenerator random) {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(random, "random");
            return payload;
        }
    },
    /** The payload is the base behind the metamorphic header, followed by random rewrite bytes. */
    METAMORPHIC {
        @Override
        public byte[] encode(byte[] payload, RandomGenerator random) {
            Objects.requireNonNull(payload, "payload");
            var rewrite = new byte[1 + random.nextInt(MAXIMUM_REWRITE_BYTES)];
            random.nextBytes(rewrite);
            return MetamorphicPayload.encode(payload, rewrite);
        }
    };

    /** The most rewrite bytes a candidate carries; a rewrite reads a few bytes per applied rule. */
    static final int MAXIMUM_REWRITE_BYTES = 64;

    /** Returns the input that carries {@code payload}, drawing any further bytes from {@code random}. */
    public abstract byte[] encode(byte[] payload, RandomGenerator random);

    /** Returns the layout the decoders of {@code technique} read. */
    public static CandidateLayout of(Technique technique) {
        return switch (Objects.requireNonNull(technique, "technique")) {
            case PBT -> CONFORMANCE;
            case MT -> METAMORPHIC;
        };
    }
}
