package io.github.tlaplus.hardening.corpus;

import java.nio.file.Path;

/** A fixed path in the on-disk corpus layout. */
public enum CorpusPath {
    ROOT("", Kind.DIRECTORY, Presence.REQUIRED, Contents.NONE),
    CONFIG("config.toml", Kind.FILE, Presence.REQUIRED, Contents.NONE),
    INPUT("00-inputs", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    PARSER_PASS("01parser-pass", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    PARSER_FAIL("01parser-fail", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    PARSER_CRASH("01parser-crash", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    TLC_INPUT("02tlc-inputs", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    TLC_PASS("02tlc-pass", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    TLC_FAIL("02tlc-fail", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    TLC_CRASH("02tlc-crash", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    APALACHE_INPUT("02apa-inputs", Kind.DIRECTORY, Presence.REQUIRED, Contents.ENTRIES),
    WORK(".work", Kind.DIRECTORY, Presence.REQUIRED, Contents.NONE),
    GENERATOR_CRASH(".work/generator-crash", Kind.DIRECTORY, Presence.LAZY, Contents.NONE),
    PARSER_SCRATCH(".work/parser-tmp", Kind.DIRECTORY, Presence.TRANSIENT, Contents.NONE),
    TLC_SCRATCH(".work/tlc-tmp", Kind.DIRECTORY, Presence.TRANSIENT, Contents.NONE),
    LOCK(".workflow.lock", Kind.FILE, Presence.LAZY, Contents.NONE);

    private final Path relativePath;
    private final Kind kind;
    private final Presence presence;
    private final Contents contents;

    CorpusPath(String relativePath, Kind kind, Presence presence, Contents contents) {
        this.relativePath = Path.of(relativePath);
        this.kind = kind;
        this.presence = presence;
        this.contents = contents;
    }

    /** Returns this location relative to the corpus root. */
    public Path relativePath() {
        return relativePath;
    }

    boolean isDirectory() {
        return kind == Kind.DIRECTORY;
    }

    boolean isRequired() {
        return presence == Presence.REQUIRED;
    }

    boolean storesEntries() {
        return contents == Contents.ENTRIES;
    }

    private enum Kind {
        FILE,
        DIRECTORY
    }

    private enum Presence {
        REQUIRED,
        LAZY,
        TRANSIENT
    }

    private enum Contents {
        NONE,
        ENTRIES
    }
}
