package io.github.tlaplus.hardening.corpus;

/**
 * A small corpus file that pins how the corpus's entries replay. The corpus stores each record as
 * opaque bytes; the workflow decides what a record says and whether a run may proceed.
 */
public enum CorpusRecord {
    /** The Apalache distribution, sources and selection of a custom operator library. */
    LIBRARY_MANIFEST(CorpusPath.LIBRARY_MANIFEST, "library-", "library manifest"),
    /** The fuzzing technique the corpus runs (ADR 0016 §1). */
    TECHNIQUE(CorpusPath.TECHNIQUE, "technique-", "technique record");

    private final CorpusPath path;
    private final String temporaryPrefix;
    private final String description;

    CorpusRecord(CorpusPath path, String temporaryPrefix, String description) {
        this.path = path;
        this.temporaryPrefix = temporaryPrefix;
        this.description = description;
    }

    CorpusPath path() {
        return path;
    }

    String temporaryPrefix() {
        return temporaryPrefix;
    }

    /** Names the record in diagnostics. */
    public String description() {
        return description;
    }
}
