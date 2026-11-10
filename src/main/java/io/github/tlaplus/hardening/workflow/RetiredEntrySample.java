package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.SplittableRandom;

/**
 * Which retired entries startup decodes (ADR 0031 §2). An entry is selected when a uniform draw
 * from its input digest and {@code salt} falls below {@code fraction}, so the choice does not depend
 * on the order or the threads of the scan. A live entry is always decoded.
 *
 * @param fraction the expected share of retired entries to decode, in [0, 1]
 * @param salt varies the selection between invocations
 */
public record RetiredEntrySample(double fraction, long salt) {
    /** The share {@code fuzztla run} decodes unless told otherwise. */
    public static final double DEFAULT_FRACTION = 0.01;

    /** Decodes every entry, as startup did before ADR 0031. */
    public static final RetiredEntrySample ALL = new RetiredEntrySample(1.0, 0);

    public RetiredEntrySample {
        if (!(fraction >= 0.0 && fraction <= 1.0)) {
            throw new IllegalArgumentException("fraction must be in [0, 1]: " + fraction);
        }
    }

    /** Samples {@code fraction} of the retired entries with a fresh salt. */
    public static RetiredEntrySample withFreshSalt(double fraction) {
        return new RetiredEntrySample(fraction, new SecureRandom().nextLong());
    }

    /** Reports whether startup decodes the input of an entry. */
    boolean decodes(CorpusEnvelope envelope) {
        if (fraction >= 1.0 || !envelope.isRetired()) {
            return true;
        }
        if (fraction <= 0.0) {
            return false;
        }
        var digest = Digests.sha256().digest(envelope.corpusInput().input());
        return new SplittableRandom(ByteBuffer.wrap(digest).getLong() ^ salt).nextDouble() < fraction;
    }
}
