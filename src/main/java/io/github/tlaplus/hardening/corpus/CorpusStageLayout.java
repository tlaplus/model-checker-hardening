package io.github.tlaplus.hardening.corpus;

/** Paths and metadata identifiers belonging to one implemented corpus stage. */
enum CorpusStageLayout {
    PARSER(
            "parser",
            "parser",
            CorpusPath.INPUT,
            CorpusPath.PARSER_SCRATCH,
            CorpusPath.PARSER_PASS,
            CorpusPath.PARSER_FAIL,
            CorpusPath.PARSER_CRASH),
    TLC(
            "tlc",
            "TLC",
            CorpusPath.TLC_INPUT,
            CorpusPath.TLC_SCRATCH,
            CorpusPath.TLC_PASS,
            CorpusPath.TLC_FAIL,
            CorpusPath.TLC_CRASH);

    private final String metadataName;
    private final String displayName;
    private final CorpusPath input;
    private final CorpusPath scratch;
    private final CorpusPath pass;
    private final CorpusPath fail;
    private final CorpusPath crash;

    CorpusStageLayout(
            String metadataName,
            String displayName,
            CorpusPath input,
            CorpusPath scratch,
            CorpusPath pass,
            CorpusPath fail,
            CorpusPath crash) {
        this.metadataName = metadataName;
        this.displayName = displayName;
        this.input = input;
        this.scratch = scratch;
        this.pass = pass;
        this.fail = fail;
        this.crash = crash;
    }

    String metadataName() {
        return metadataName;
    }

    String displayName() {
        return displayName;
    }

    CorpusPath input() {
        return input;
    }

    CorpusPath scratch() {
        return scratch;
    }

    CorpusPath result(CorpusVerdict verdict) {
        return switch (verdict) {
            case PASS -> pass;
            case FAIL -> fail;
            case CRASH -> crash;
        };
    }
}
