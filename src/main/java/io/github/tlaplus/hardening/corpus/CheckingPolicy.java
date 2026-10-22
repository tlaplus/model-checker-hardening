package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import java.util.Objects;

/**
 * Which checkers a workflow run feeds and how the aggregator judges their verdicts. The workflow
 * derives it from the configuration and the corpus records it has verified; the corpus only
 * applies it.
 *
 * @param checkers the checker branches the corpus runs
 * @param oracle how the aggregator judges their results
 * @param expectations what each entry predicts, for an oracle that judges against a prediction
 *     (ADR 0023 §5); the corpus cannot decode, so the workflow supplies the replay
 */
@ArchRole(Role.POLICY)
@Key
public record CheckingPolicy(CheckerSet checkers, Oracle oracle, ExpectedOutcomes expectations) {
    /** The policy of every corpus before ADR 0016: the default checkers, judged by conformance. */
    public static final CheckingPolicy DEFAULT = new CheckingPolicy(CheckerSet.DEFAULT, Oracle.CONFORMANCE);

    public CheckingPolicy {
        Objects.requireNonNull(checkers, "checkers");
        Objects.requireNonNull(oracle, "oracle");
        Objects.requireNonNull(expectations, "expectations");
        if (oracle.needsExpectation() && expectations == ExpectedOutcomes.NONE) {
            throw new IllegalArgumentException("the " + oracle + " oracle needs expected outcomes");
        }
    }

    /** A policy whose oracle needs no expected outcome. */
    public CheckingPolicy(CheckerSet checkers, Oracle oracle) {
        this(checkers, oracle, ExpectedOutcomes.NONE);
    }

    /**
     * Returns the policy of a corpus that runs {@code technique} with {@code checkers}, whose
     * entries {@code expectations} predicts when the technique's oracle needs it.
     */
    public static CheckingPolicy of(Technique technique, CheckerSet checkers, ExpectedOutcomes expectations) {
        return new CheckingPolicy(checkers, technique.oracle(), expectations);
    }
}
