package io.github.tlaplus.hardening.corpus;

/**
 * Counts the evaluated nodes, expression constructs and construct edges of one stored input, and
 * names the rewrite rules it applied, typically by replaying it through the generator.
 *
 * <p>Callers may call one instance from several threads at once, one input per call, so an
 * implementation must not share mutable state between calls. A call that throws a {@link
 * RuntimeException} or {@link StackOverflowError} marks only that input as not replayable.
 */
@FunctionalInterface
public interface InputAnalysis {
    ReplayedInput analyze(CorpusInput input);
}
