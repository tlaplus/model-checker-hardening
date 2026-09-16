package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A way in which a TLC run explored almost nothing, as the calibration table of ADR 0008 defines
 * it. Declaration order is that table's order.
 *
 * <p>A pattern matches only on measured values: a count TLC did not measure matches no pattern
 * that tests it. The config name is how {@code [mutator] shallow_patterns} selects a pattern.
 */
public enum ShallowPattern {
    /** A pass with no initial state. */
    VACUOUS_PASS("vacuous_pass", CorpusVerdict.PASS, metrics -> equals(metrics, ExplorationCount.INIT_STATES, 0)),
    /** A counterexample found before the initial states were complete. */
    INITIAL_STATE_VIOLATION("initial_state_violation", CorpusVerdict.COUNTEREXAMPLE, ShallowPattern::stoppedInInit),
    /** A failure before the initial states were complete. */
    EARLY_FAILURE("early_failure", CorpusVerdict.FAIL, ShallowPattern::stoppedInInit),
    /** No disjunct of the next-state action produced a new state. */
    NO_DISCOVERING_ACTION("no_discovering_action", null,
            metrics -> equals(metrics, ExplorationCount.ACTIONS_DISCOVERING, 0)),
    /** At most one state remains once the step counter is projected away. */
    COUNTER_ONLY_PROGRESS("counter_only_progress", null,
            metrics -> metrics.count(ExplorationCount.PROJECTED_STATES).stream().anyMatch(value -> value <= 1));

    private final String configName;
    private final CorpusVerdict verdict;
    private final Predicate<ExplorationMetrics> metrics;

    /**
     * @param verdict the TLC verdict the pattern requires, or {@code null} for any verdict
     */
    ShallowPattern(String configName, CorpusVerdict verdict, Predicate<ExplorationMetrics> metrics) {
        this.configName = configName;
        this.verdict = verdict;
        this.metrics = metrics;
    }

    public String configName() {
        return configName;
    }

    /** Reports whether a TLC stage record shows this pattern. A record without metrics shows none. */
    public boolean matches(StageRecord tlc) {
        Objects.requireNonNull(tlc, "tlc");
        return (verdict == null || tlc.verdict() == verdict)
                && tlc.metrics().filter(metrics).isPresent();
    }

    /** Returns the pattern with this config name, or empty for a name this build does not know. */
    public static Optional<ShallowPattern> fromConfigName(String configName) {
        return Arrays.stream(values()).filter(pattern -> pattern.configName.equals(configName)).findFirst();
    }

    private static boolean stoppedInInit(ExplorationMetrics metrics) {
        return metrics.phase().filter(phase -> phase == ExplorationPhase.INIT).isPresent();
    }

    private static boolean equals(ExplorationMetrics metrics, ExplorationCount count, long expected) {
        return metrics.count(count).stream().anyMatch(value -> value == expected);
    }
}
