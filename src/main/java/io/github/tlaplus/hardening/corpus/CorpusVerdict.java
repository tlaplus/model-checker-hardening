package io.github.tlaplus.hardening.corpus;

/** A verdict supported by the implemented corpus stages. */
public enum CorpusVerdict {
    PASS("pass", "passed"),
    COUNTEREXAMPLE("counterexample", "counterexamples"),
    FAIL("fail", "failed"),
    CRASH("crashed", "crashed");

    private final String encodedName;
    private final String countLabel;

    CorpusVerdict(String encodedName, String countLabel) {
        this.encodedName = encodedName;
        this.countLabel = countLabel;
    }

    public String encodedName() {
        return encodedName;
    }

    /** Returns the label used when reporting how many results have this verdict. */
    public String countLabel() {
        return countLabel;
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
