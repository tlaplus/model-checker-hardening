package io.github.tlaplus.hardening.gen.rewrite;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a rule does to the checkers' assignments (ADR 0017 §5.3). TLC treats {@code x' = e} as an
 * assignment only under {@code /\}, {@code \/}, an {@code IF} branch, an {@code \E} body or a
 * {@code LET} body, and evaluates conjuncts from left to right. A Boolean parameter bound to a
 * primed subterm may therefore move only from such a position to such a position, once, and in the
 * same order. Apalache moreover requires every branch of a disjunction or an {@code IF} to assign
 * every variable, so each branch of the replacement must hold the primed parameters that some
 * branch of the pattern holds: {@code P \/ FALSE} is refused for {@code x' = 1}.
 *
 * @param preserved the Boolean parameters that both the pattern and the replacement hold exactly once
 *     in an assignment position
 * @param pattern how the pattern holds the Boolean parameters
 * @param replacement how the replacement holds the Boolean parameters
 * @param primed the parameters the replacement primes, which TLC assigns only when they are names
 * @param unchanged the parameters the replacement keeps UNCHANGED, which TLC assigns only when
 *     they are names or tuples of names
 */
public record AssignmentFacts(Set<String> preserved, Side pattern, Side replacement, Set<String> primed,
        Set<String> unchanged) {
    public AssignmentFacts {
        preserved = Set.copyOf(Objects.requireNonNull(preserved, "preserved"));
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(replacement, "replacement");
        primed = Set.copyOf(Objects.requireNonNull(primed, "primed"));
        unchanged = Set.copyOf(Objects.requireNonNull(unchanged, "unchanged"));
    }

    /**
     * How one side of a rule holds its Boolean parameters.
     *
     * @param order the parameters in the order the side mentions them first
     * @param branches for each branch through the side's disjunctions and {@code IF} branches, the
     *     parameters it holds in an assignment position
     */
    public record Side(List<String> order, Set<Set<String>> branches) {
        public Side {
            order = List.copyOf(Objects.requireNonNull(order, "order"));
            branches = Objects.requireNonNull(branches, "branches").stream()
                    .map(Set::copyOf)
                    .collect(Collectors.toUnmodifiableSet());
        }

        private List<String> order(Set<String> parameters) {
            return order.stream().filter(parameters::contains).toList();
        }

        private Set<Set<String>> branches(Set<String> parameters) {
            return branches.stream()
                    .map(branch -> branch.stream().filter(parameters::contains).collect(Collectors.toUnmodifiableSet()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /**
     * Whether a match that binds exactly {@code primed} to subterms reading a primed variable keeps
     * every assignment the checkers made before.
     */
    public boolean admits(Set<String> primed) {
        return preserved.containsAll(primed)
                && pattern.order(primed).equals(replacement.order(primed))
                && pattern.branches(primed).containsAll(replacement.branches(primed));
    }
}
