package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusRecord;
import io.github.tlaplus.hardening.corpus.Technique;
import java.io.IOException;

/** The technique a corpus records on its first run, which every later run must repeat. */
public final class CorpusTechnique {
    private static final ReplayRecord RECORD = new ReplayRecord(
            CorpusRecord.TECHNIQUE,
            Technique.UNRECORDED.encodedName(),
            (saved, expected) -> "this corpus runs --how=" + saved + ", not --how=" + expected
                    + "; initialize a new corpus for --how=" + expected);

    private CorpusTechnique() {}

    /** Records {@code technique} in an empty corpus, or checks that the corpus runs it. */
    public static void verify(CorpusDirectory corpus, Technique technique, boolean initialize)
            throws IOException, CorpusException, WorkflowException {
        RECORD.verify(corpus, technique.encodedName(), initialize);
    }

    /** Returns the technique a corpus runs; a corpus that records none runs {@code pbt}. */
    public static Technique read(CorpusDirectory corpus)
            throws IOException, CorpusException, WorkflowException {
        var saved = RECORD.read(corpus);
        return Technique.fromEncodedName(saved).orElseThrow(() -> new WorkflowException(
                "unknown technique '" + saved + "' in " + corpus.resolve(CorpusPath.TECHNIQUE)));
    }
}
