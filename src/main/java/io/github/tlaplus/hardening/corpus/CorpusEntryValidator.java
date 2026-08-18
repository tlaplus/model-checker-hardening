package io.github.tlaplus.hardening.corpus;

import java.nio.file.Path;

/**
 * Checks that a stored payload is still one this FuzzTLA build can use.
 *
 * <p>Whether a byte string decodes to an expression is a property of the generator, not of the
 * corpus, so recovery takes this decision from its caller. An implementation reports a payload it
 * cannot use as a {@link CorpusException} naming the entry.
 */
@FunctionalInterface
public interface CorpusEntryValidator {
    /**
     * Accepts a payload, or reports why this corpus entry is unusable. A failure that escapes as a
     * runtime exception or {@link StackOverflowError} is treated as a generator crash: the corpus
     * preserves the payload under {@code .work/generator-crash} before reporting it.
     */
    void validate(Path entry, byte[] input) throws CorpusException;

    /** Accepts every payload, for operations that read an entry rather than validate it. */
    CorpusEntryValidator NONE = (entry, input) -> {};
}
