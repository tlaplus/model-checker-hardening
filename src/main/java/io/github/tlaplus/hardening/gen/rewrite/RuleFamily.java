package io.github.tlaplus.hardening.gen.rewrite;

/** Which rules a rule module may hold, and where they apply (ADR 0017, ADR 0027 §1). */
public enum RuleFamily {
    /**
     * Rules {@code A = B} or {@code A <=> B} without temporal operators: the rewrites of every body
     * of a metamorphic pair (ADR 0017) and the edge guards of graph inputs (ADR 0023 §6).
     */
    REWRITES(false),
    /**
     * Rules {@code A <=> B} that may apply temporal operators: the rewrites of properties and
     * fairness conditions (ADR 0027).
     */
    TEMPORAL_REWRITES(true);

    private final boolean temporal;

    RuleFamily(boolean temporal) {
        this.temporal = temporal;
    }

    /**
     * Whether the family's rules may apply temporal operators. Such a rule must be {@code A <=> B},
     * since SANY rejects {@code =} between temporal formulas.
     */
    public boolean temporal() {
        return temporal;
    }
}
