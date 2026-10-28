package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.workflow.library.LibraryManifest;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.io.IOException;

/**
 * Decodes a corpus's entries as they were admitted: with the decoders of the configured technique,
 * and only while the libraries they load match the corpus's manifests.
 */
@ArchRole(Role.FACADE)
@Key
public final class CorpusReplay {
    private CorpusReplay() {}

    /** Returns the decoders a reader such as {@code print} or {@code export-db} replays entries with. */
    public static SpecDecoders decoders(CorpusDirectory corpus, FuzzTlaConfig config)
            throws IOException, CorpusException, WorkflowException {
        var technique = config.workflow().technique();
        CorpusRecords.TECHNIQUE.requireUnchanged(corpus, technique);
        var decoders = SpecDecoders.prepare(config, technique);
        verify(corpus, decoders, false);
        return decoders;
    }

    /**
     * Checks the manifests of the libraries {@code decoders} loaded; a writer records them in an
     * empty corpus with {@code initialize}, under the corpus lock.
     */
    static void verify(CorpusDirectory corpus, SpecDecoders decoders, boolean initialize)
            throws IOException, CorpusException, WorkflowException {
        LibraryManifest.verify(corpus, decoders.libraryManifest(), initialize);
        CorpusRecords.REWRITE_LIBRARY.verify(corpus, decoders.rewriteManifest(), initialize);
    }
}
