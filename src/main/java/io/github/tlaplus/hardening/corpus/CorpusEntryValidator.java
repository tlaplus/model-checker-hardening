package io.github.tlaplus.hardening.corpus;

import java.nio.file.Path;

/**
 * Checks that a stored input is still one this FuzzTLA build can use.
 *
 * <p>Which kinds a run consumes, and whether a byte string decodes under its generator
 * configuration, are properties of the caller rather than of the corpus, so recovery takes both
 * decisions from here. The whole {@link CorpusInput} is supplied because the kind is part of that
 * decision. An implementation reports an input it cannot use as a {@link CorpusException} naming
 * the entry.
 */
@FunctionalInterface
public interface CorpusEntryValidator {
    /**
     * Accepts an input, or reports why this corpus entry is unusable. A failure that escapes as a
     * runtime exception or {@link StackOverflowError} is treated as a generator crash: the corpus
     * preserves the payload under {@code .work/generator-crash} before reporting it.
     */
    void validate(Path entry, CorpusInput input) throws CorpusException;

    /** Accepts every input, for operations that read an entry rather than validate it. */
    CorpusEntryValidator NONE = (entry, input) -> {};
}
