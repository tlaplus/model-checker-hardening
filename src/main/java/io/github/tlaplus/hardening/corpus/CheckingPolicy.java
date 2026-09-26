package io.github.tlaplus.hardening.corpus;

import java.util.Objects;

/**
 * Which checkers a workflow run feeds and how the aggregator judges their verdicts. The workflow
 * derives it from the configuration and the corpus records it has verified; the corpus only
 * applies it.
 */
public record CheckingPolicy(CheckerSet checkers, Oracle oracle) {
    /** The policy of every corpus before ADR 0016: both checkers, judged by conformance. */
    public static final CheckingPolicy DEFAULT = new CheckingPolicy(CheckerSet.ALL, Oracle.CONFORMANCE);

    public CheckingPolicy {
        Objects.requireNonNull(checkers, "checkers");
        Objects.requireNonNull(oracle, "oracle");
    }

    /** Returns the policy of a corpus that runs {@code technique} with {@code checkers}. */
    public static CheckingPolicy of(Technique technique, CheckerSet checkers) {
        return new CheckingPolicy(checkers, technique.oracle());
    }
}
