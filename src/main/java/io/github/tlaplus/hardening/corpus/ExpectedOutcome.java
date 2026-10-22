package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The checker verdict an input predicts, independently of any checker (ADR 0023 §4): a graph input
 * predicts a counterexample of known length when its target is reachable within the step bound,
 * and a pass otherwise.
 *
 * @param verdict {@link CorpusVerdict#PASS} or {@link CorpusVerdict#COUNTEREXAMPLE}
 * @param traceLength the transitions of a shortest counterexample, present exactly for a
 *     counterexample
 */
@ArchRole(Role.MODEL)
@Key
public record ExpectedOutcome(CorpusVerdict verdict, OptionalInt traceLength) {
    public ExpectedOutcome {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(traceLength, "traceLength");
        Preconditions.require(verdict == CorpusVerdict.PASS || verdict == CorpusVerdict.COUNTEREXAMPLE,
                "an expected outcome is a pass or a counterexample");
        Preconditions.require(traceLength.isPresent() == (verdict == CorpusVerdict.COUNTEREXAMPLE),
                "exactly a counterexample has a trace length");
        traceLength.ifPresent(length -> Preconditions.requireNonnegative(length, "traceLength"));
    }

    /** The outcome of an input whose invariant holds in every reachable state. */
    public static ExpectedOutcome pass() {
        return new ExpectedOutcome(CorpusVerdict.PASS, OptionalInt.empty());
    }

    /** The outcome of an input whose invariant fails first after {@code traceLength} transitions. */
    public static ExpectedOutcome counterexample(int traceLength) {
        return new ExpectedOutcome(CorpusVerdict.COUNTEREXAMPLE, OptionalInt.of(traceLength));
    }

    /**
     * Reports whether one checker result is consistent with this outcome: the checker reports the
     * expected verdict, or fails, which a partial term causes on any input (ADR 0023 §5).
     */
    boolean admits(StageRecord result) {
        Objects.requireNonNull(result, "result");
        return result.verdict() == verdict || result.verdict() == CorpusVerdict.FAIL;
    }
}
