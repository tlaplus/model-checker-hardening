package io.github.tlaplus.hardening.checker;

/** How far a model checker got before its verdict was fixed. */
public enum ExplorationPhase {
    /** The run stopped before its initial states were complete. */
    INIT("init"),
    /** The run stopped after its initial states were complete, while exploring successors. */
    EXPLORE("explore"),
    /** The search finished. */
    COMPLETE("complete");

    private final String encodedName;

    ExplorationPhase(String encodedName) {
        this.encodedName = encodedName;
    }

    public String encodedName() {
        return encodedName;
    }

    public static ExplorationPhase fromEncodedName(String encodedName) {
        for (var phase : values()) {
            if (phase.encodedName.equals(encodedName)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("unsupported exploration phase: " + encodedName);
    }
}
