package io.github.tlaplus.hardening.database;

import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.corpus.CorpusInput;

/**
 * Counts the evaluated nodes and expression constructs of one stored input, typically by replaying it through the
 * generator.
 *
 * <p>The export calls one instance from several threads at once, one input per call, so an
 * implementation must not share mutable state between calls. A call that throws a {@link
 * RuntimeException} or {@link StackOverflowError} marks only that input as not replayable.
 */
@FunctionalInterface
public interface InputAnalysis {
    ExprCounts analyze(CorpusInput input);
}
