package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.io.IOException;

/**
 * Decodes a corpus's entries as they were admitted: with the decoders of the configured technique,
 * and the libraries, rules and tools the corpus holds (ADR 0025).
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
        return SpecDecoders.prepare(config, technique);
    }
}
