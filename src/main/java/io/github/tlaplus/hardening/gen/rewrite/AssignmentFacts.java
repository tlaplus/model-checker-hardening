package io.github.tlaplus.hardening.gen.rewrite;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a rule does to TLC's assignments (ADR 0017 §5.3). TLC treats {@code x' = e} as an
 * assignment only under {@code /\}, {@code \/}, an {@code IF} branch, an {@code \E} body or a
 * {@code LET} body, and evaluates conjuncts from left to right. A Boolean parameter bound to a
 * primed subterm may therefore move only to such a position, once, and in the same order.
 *
 * @param preserved the Boolean parameters the replacement keeps exactly once in an assignment
 *     position
 * @param patternOrder the Boolean parameters in the order the pattern mentions them first
 * @param replacementOrder the Boolean parameters in the order the replacement mentions them first
 */
public record AssignmentFacts(Set<String> preserved, List<String> patternOrder, List<String> replacementOrder) {
    public AssignmentFacts {
        preserved = Set.copyOf(Objects.requireNonNull(preserved, "preserved"));
        patternOrder = List.copyOf(Objects.requireNonNull(patternOrder, "patternOrder"));
        replacementOrder = List.copyOf(Objects.requireNonNull(replacementOrder, "replacementOrder"));
    }

    /**
     * Whether a match that binds exactly {@code primed} to subterms reading a primed variable keeps
     * every assignment TLC made before.
     */
    public boolean admits(Set<String> primed) {
        if (!preserved.containsAll(primed)) {
            return false;
        }
        return patternOrder.stream().filter(primed::contains).toList()
                .equals(replacementOrder.stream().filter(primed::contains).toList());
    }
}
