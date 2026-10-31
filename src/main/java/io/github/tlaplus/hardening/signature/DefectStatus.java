package io.github.tlaplus.hardening.signature;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Whether the defect behind a signature is still present. A database spells the status in lowercase.
 *
 * <p>Only an open signature quarantines a candidate. A candidate that matches only closed signatures
 * is admitted, so the tools check the shapes of fixed defects again and a regression shows up.
 */
public enum DefectStatus {
    /** Some referenced defect is unfixed, or the signature records a conformance difference. */
    OPEN(true),
    /** Every referenced finding is fixed. */
    CLOSED(false);

    private final boolean quarantines;

    DefectStatus(boolean quarantines) {
        this.quarantines = quarantines;
    }

    /** Whether a candidate that matches a signature with this status is kept out of the corpus. */
    public boolean quarantines() {
        return quarantines;
    }

    /** The spelling in a database file. */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<DefectStatus> fromSpelling(String spelling) {
        return Arrays.stream(values()).filter(status -> status.spelling().equals(spelling)).findFirst();
    }
}
