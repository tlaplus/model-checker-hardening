package io.github.tlaplus.hardening.config;

/**
 * The reference checker whose conventions a generic checker follows (ADR 0033 §2): the
 * configuration file FuzzTLA writes for it and how its exit status becomes a verdict. The workflow
 * keys those conventions by this enum.
 */
public enum GenericCompatibility {
    /** TLC's configuration file and exit statuses. */
    TLC("tlc"),
    /** Apalache's entry points, unrolling length and exit statuses. */
    APALACHE("apalache");

    private final String configName;

    GenericCompatibility(String configName) {
        this.configName = configName;
    }

    /** Returns the name of this compatibility in {@code [generic] compatibility}. */
    public String configName() {
        return configName;
    }
}
