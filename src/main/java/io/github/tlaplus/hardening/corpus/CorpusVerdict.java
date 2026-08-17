package io.github.tlaplus.hardening.corpus;

/** A verdict supported by the implemented corpus stages. */
enum CorpusVerdict {
    PASS("pass"),
    FAIL("fail"),
    CRASH("crashed");

    private final String encodedName;

    CorpusVerdict(String encodedName) {
        this.encodedName = encodedName;
    }

    String encodedName() {
        return encodedName;
    }

    static CorpusVerdict fromEncodedName(String encodedName) {
        for (var verdict : values()) {
            if (verdict.encodedName.equals(encodedName)) {
                return verdict;
            }
        }
        throw new IllegalArgumentException("unsupported corpus verdict: " + encodedName);
    }
}
