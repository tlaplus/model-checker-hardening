package io.github.tlaplus.hardening.pbt;

/** Reports that PBT could not populate the requested corpus within its attempt budget. */
public final class PbtException extends Exception {
    private final PbtRunSummary summary;

    public PbtException(String message, PbtRunSummary summary) {
        super(message);
        this.summary = summary;
    }

    public PbtRunSummary summary() {
        return summary;
    }
}
