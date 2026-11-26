package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * How many entries a corpus holds, split the way {@code workflow.max_entries} counts them (ADR 0034
 * §6): {@code sized} entries share the generations' sizes and are bounded by {@code maximum}, while
 * {@code replays} come on top of them and are not.
 */
public record CorpusEntryCounts(long sized, long replays, long maximum) {
    public CorpusEntryCounts {
        Preconditions.requireNonnegative(sized, "sized");
        Preconditions.requireNonnegative(replays, "replays");
        Preconditions.requireNonnegative(maximum, "maximum");
    }

    /** Returns every entry of the corpus, replays included. */
    public long total() {
        return Math.addExact(sized, replays);
    }
}
