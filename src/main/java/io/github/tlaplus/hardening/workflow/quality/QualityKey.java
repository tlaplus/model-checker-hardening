package io.github.tlaplus.hardening.workflow.quality;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.corpus.Ranking;
import io.github.tlaplus.hardening.corpus.RankingInput;
import java.util.Comparator;
import java.util.Objects;

/**
 * The order in which the quality gate ranks admissible entries, best first (ADR 0010, ADR 0024
 * §2).
 *
 * <p>The key compares the components of a {@link Ranking} lexicographically, higher first. A count
 * TLC did not measure ranks as 0. Ties go to the shorter input and then to the smaller digest, so
 * the order is total.
 */
@ArchRole(Role.POLICY)
@Key
final class QualityKey {
    /** What the key ranks: what the ranking reads of an entry, its input length and its digest. */
    record Ranked(RankingInput input, int inputBytes, String digest) {
        Ranked {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(digest, "digest");
        }
    }

    private QualityKey() {}

    /** Orders the best entry under {@code ranking} first. */
    static Comparator<Ranked> bestFirst(Ranking ranking) {
        Objects.requireNonNull(ranking, "ranking");
        Comparator<Ranked> order = (left, right) -> 0;
        for (var component : ranking.components()) {
            order = order.thenComparing(Comparator.comparingLong(
                    (Ranked ranked) -> component.value(ranked.input())).reversed());
        }
        return order.thenComparingInt(Ranked::inputBytes).thenComparing(Ranked::digest);
    }
}
