package io.github.tlaplus.hardening.corpus;

/** How many entries one stage has produced under each verdict. */
public record StageEntryCounts(long passed, long failed, long crashed) {
    public StageEntryCounts {
        if (passed < 0 || failed < 0 || crashed < 0) {
            throw new IllegalArgumentException("corpus counters must be nonnegative");
        }
    }

    /** Returns the counts of a stage that has produced no verdict yet. */
    public static StageEntryCounts empty() {
        return new StageEntryCounts(0, 0, 0);
    }

    public long count(CorpusVerdict verdict) {
        return switch (verdict) {
            case PASS -> passed;
            case FAIL -> failed;
            case CRASH -> crashed;
        };
    }

    /** Returns the number of entries this stage has produced a verdict for. */
    public long processed() {
        return passed + failed + crashed;
    }
}
