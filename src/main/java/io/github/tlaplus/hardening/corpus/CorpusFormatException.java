package io.github.tlaplus.hardening.corpus;

import java.io.IOException;

/**
 * Reports stored corpus data that does not follow the documented CBOR format.
 *
 * <p>Raised for both stored documents: an input envelope and the workflow-statistics aggregate.
 * The message names the offending value by its path in the document.
 */
public final class CorpusFormatException extends IOException {
    public CorpusFormatException(String message) {
        super(message);
    }

    public CorpusFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
