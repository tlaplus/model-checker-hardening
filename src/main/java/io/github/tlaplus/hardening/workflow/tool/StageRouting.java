package io.github.tlaplus.hardening.workflow.tool;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Where a {@link ToolStage} takes its inputs from, how it bounds its result directories, and where a
 * recorded result goes next.
 *
 * <p>The parser and the model checkers share the tool loop and differ only here. Every checker
 * verdict stays in the checker's result directories, so a checker reserves capacity before it
 * runs. A parser pass fans out downstream and leaves the parser's directories, so the parser
 * reserves capacity only once it knows the verdict stays.
 */
public interface StageRouting {
    CpuBudget.Priority priority();

    /** Reads an entry this stage owns. */
    CorpusInput read(CorpusDirectory corpus, Path source) throws IOException, CorpusException;

    /** Reserves result capacity before the tool runs; {@code false} stops the stage. */
    default boolean reserveBeforeRun() {
        return true;
    }

    /**
     * Reserves result capacity for a verdict; {@code false} stops the stage and discards the
     * verdict, leaving the input where it is.
     */
    default boolean reserveFor(CorpusVerdict verdict) {
        return true;
    }

    /** Records a result durably and returns where the entry now is. */
    Path complete(CorpusDirectory corpus, Path source, StageResult result)
            throws IOException, CorpusException;

    /** Hands a recorded entry to the stages downstream. */
    void forward(CorpusDirectory corpus, Path destination, CorpusVerdict verdict) throws Exception;

    /** Closes the downstream queues this stage alone feeds, once it can add nothing to them. */
    default void closeOutputs() {}
}
