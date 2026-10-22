package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import java.util.Optional;

/**
 * Predicts the outcome of one stored input, typically by replaying it through the generator
 * (ADR 0023 §5). The corpus asks it whenever it judges an aggregation, at run time and at recovery.
 *
 * <p>Callers may call one instance from several threads at once, one input per call, so an
 * implementation must not share mutable state between calls. An input that predicts nothing, such
 * as one of another kind, yields empty. An input that cannot be replayed throws a
 * {@link RuntimeException}; the corpus has validated every stored payload before it asks.
 */
@ArchRole(Role.CONTRACT)
@FunctionalInterface
public interface ExpectedOutcomes {
    /** Predicts nothing, for a corpus whose oracle needs no prediction. */
    ExpectedOutcomes NONE = _ -> Optional.empty();

    Optional<ExpectedOutcome> expected(CorpusInput input);
}
