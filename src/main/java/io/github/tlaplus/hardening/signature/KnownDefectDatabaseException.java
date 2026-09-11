package io.github.tlaplus.hardening.signature;

/** A known-defect database that cannot be read, or that does not follow the database format. */
public final class KnownDefectDatabaseException extends Exception {
    public KnownDefectDatabaseException(String message) {
        super(message);
    }

    public KnownDefectDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
