package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusRecord;
import io.github.tlaplus.hardening.corpus.Technique;

/**
 * The one corpus record still read: the technique of a corpus from before ADR 0025, whose
 * configuration must now repeat it.
 */
public final class CorpusRecords {
    /**
     * The technique a corpus from before ADR 0025 recorded on its first run, which {@code
     * [workflow] technique} must repeat. No run writes it any more; delete it once such corpora
     * are gone.
     */
    public static final ReplayRecord<Technique> TECHNIQUE = new ReplayRecord<>(
            CorpusRecord.TECHNIQUE,
            Technique.UNRECORDED,
            new ReplayRecord.Codec<>(Technique::encodedName, Technique::fromEncodedName),
            (saved, expected) -> "this corpus holds " + saved + " entries, but its config.toml sets technique = \""
                    + expected + "\"; set technique = \"" + saved + "\" under [workflow]");

    private CorpusRecords() {}
}
