package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.gen.InputKind;
import java.util.List;
import java.util.Objects;

/**
 * One contiguous range of target entries the input stage admits: which input kind, for which
 * generation, from which seed, how many entries from each candidate source, and with at most how
 * many workers.
 *
 * <p>A generation is admitted as one or more ranges — its mutant prefix and its PBT suffix
 * (ADR 0010) — which is what {@code firstTarget} distinguishes. Two ranges of one generation must
 * not overlap: a target's ordinal seeds its candidate stream, so two targets with the same ordinal
 * replay the same stream.
 *
 * @param generation the generation every admitted entry records
 * @param initialEntries the entries before this generation, for the entry numbers in diagnostics
 * @param workerLimit the most generator workers that may share this range
 * @param firstTarget the ordinal of this range's first target within its generation
 * @param quotas the sources in the order their targets are numbered, each with its entry count
 */
public record GenerationPlan(
        InputKind kind,
        int generation,
        long seed,
        long initialEntries,
        int workerLimit,
        long firstTarget,
        List<Quota> quotas) {
    /** How many entries one candidate source contributes. */
    public record Quota(CandidateSource source, long entries) {
        public Quota {
            Objects.requireNonNull(source, "source");
            Preconditions.requireNonnegative(entries, "entries");
        }
    }

    public GenerationPlan {
        Objects.requireNonNull(kind, "kind");
        Preconditions.requireNonnegative(generation, "generation");
        Preconditions.requireNonnegative(initialEntries, "initialEntries");
        Preconditions.requireNonnegative(seed, "seed");
        Preconditions.requirePositive(workerLimit, "workerLimit");
        Preconditions.requireNonnegative(firstTarget, "firstTarget");
        quotas = List.copyOf(Objects.requireNonNull(quotas, "quotas"));
    }

    /** Returns how many entries the stage has to add. */
    long missingEntries() {
        return quotas.stream().mapToLong(Quota::entries).sum();
    }

    /** Returns the source of the target with this zero-based ordinal. */
    CandidateSource source(long target) {
        var remaining = target;
        for (var quota : quotas) {
            if (remaining < quota.entries()) {
                return quota.source();
            }
            remaining -= quota.entries();
        }
        throw new IllegalArgumentException("target " + target + " is outside the plan");
    }
}
