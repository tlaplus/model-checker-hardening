package io.github.tlaplus.hardening.gen.rewrite;

/**
 * Which side of a metamorphic pair the checker explores (ADR 0016 §3). The other side is checked:
 * its initial predicate at {@code step = 0} and its action on every transition. Across a corpus
 * both orientations occur, so each side generates successors in some entries.
 */
public enum Orientation {
    /** The original is explored and the rewrite is checked; an even orientation marker. */
    EXPLORE_ORIGINAL,
    /** The rewrite is explored and the original is checked; an odd orientation marker. */
    EXPLORE_REWRITE;

    /** Returns the orientation an orientation marker selects. */
    public static Orientation of(boolean marker) {
        return marker ? EXPLORE_REWRITE : EXPLORE_ORIGINAL;
    }

    /** Returns the side the checker explores. */
    public <T> T explored(T original, T rewritten) {
        return this == EXPLORE_ORIGINAL ? original : rewritten;
    }

    /** Returns the side the checker checks. */
    public <T> T checked(T original, T rewritten) {
        return this == EXPLORE_ORIGINAL ? rewritten : original;
    }
}
