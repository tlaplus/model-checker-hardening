package io.github.tlaplus.hardening.database;

/** Reports that the corpus database cannot be written. */
public final class CorpusDatabaseException extends Exception {
    public CorpusDatabaseException(String message) {
        super(message);
    }

    public CorpusDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
