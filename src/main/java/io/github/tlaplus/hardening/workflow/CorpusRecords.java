package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.corpus.CorpusRecord;
import io.github.tlaplus.hardening.corpus.Technique;

/**
 * The corpus records that pin how entries replay (ADR 0017 §6), and the technique record of a
 * corpus from before ADR 0025, whose configuration must now repeat it.
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

    /**
     * The rule module of a corpus: the rewrites of a metamorphic corpus, or the edge guards of
     * graph inputs otherwise (ADR 0023 §6). Its manifest pins the sources, the module and the
     * weights, since rule order and text are part of the byte encoding. Empty without rules.
     */
    public static final ReplayRecord<String> REWRITE_LIBRARY = new ReplayRecord<>(
            CorpusRecord.REWRITE_LIBRARY,
            "",
            ReplayRecord.Codec.TEXT,
            (saved, expected) -> saved.isEmpty()
                    ? "rewrite rule manifest is missing; start with an empty corpus"
                    : "rewrite rules changed: restore their sources, module, weights and Apalache "
                            + "distribution, or initialize a new corpus");

    private CorpusRecords() {}
}
