package io.github.tlaplus.hardening.pbt;

/** Reports an invalid corpus layout or corpus entry. */
public final class CorpusException extends Exception {
    public CorpusException(String message) {
        super(message);
    }

    public CorpusException(String message, Throwable cause) {
        super(message, cause);
    }
}
