package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.util.List;
import java.util.Objects;

/**
 * How a mutated input was derived: the digest of the parent it was mutated from and the operators
 * applied to the parent, in order.
 */
public record Mutation(String parent, List<MutationOperator> operators) {
    public Mutation {
        Objects.requireNonNull(parent, "parent");
        Preconditions.require(CorpusLayout.isDigest(parent), "parent must be a payload digest");
        operators = List.copyOf(Objects.requireNonNull(operators, "operators"));
        Preconditions.require(!operators.isEmpty(), "a mutation applies at least one operator");
    }
}
