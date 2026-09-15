package io.github.tlaplus.hardening.database;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The static features of one input.
 *
 * @param evaluatedNodes the subexpressions the checkers evaluate
 * @param operators the applications of each operator in them, keyed by operator name
 */
public record InputFeatures(long evaluatedNodes, Map<String, Long> operators) {
    public InputFeatures {
        Preconditions.requireNonnegative(evaluatedNodes, "evaluatedNodes");
        var copy = new TreeMap<String, Long>();
        Objects.requireNonNull(operators, "operators").forEach((name, occurrences) -> {
            Objects.requireNonNull(name, "name");
            Preconditions.requireNonnegative(Objects.requireNonNull(occurrences, "occurrences"), name);
            copy.put(name, occurrences);
        });
        operators = Collections.unmodifiableMap(copy);
    }
}
