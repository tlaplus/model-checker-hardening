package io.github.tlaplus.hardening.corpus;

import java.io.IOException;

/** Reports a corpus input that does not follow the documented CBOR format. */
public final class CorpusInputFormatException extends IOException {
    public CorpusInputFormatException(String message) {
        super(message);
    }

    public CorpusInputFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
