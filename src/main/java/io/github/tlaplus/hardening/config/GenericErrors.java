package io.github.tlaplus.hardening.config;

/**
 * How much of a generic checker's exit status the aggregator compares (ADR 0033 §7). A tool that
 * reads the reference checker's inputs need not share its exit statuses: with {@link #NONZERO}, only
 * whether the tool reported an error counts.
 */
public enum GenericErrors {
    /** The reference checker's exit statuses: pass, counterexample, failure and crash apart. */
    EXACT("exact"),
    /**
     * Status 0 is a pass and any other status an error that the aggregator matches with a
     * counterexample or a failure of another checker. A process killed by a fatal signal, such as
     * status 139 for SIGSEGV, and a timeout remain crashes.
     */
    NONZERO("nonzero");

    private final String configName;

    GenericErrors(String configName) {
        this.configName = configName;
    }

    /** Returns the name of this mode in {@code [generic] errors}. */
    public String configName() {
        return configName;
    }
}
