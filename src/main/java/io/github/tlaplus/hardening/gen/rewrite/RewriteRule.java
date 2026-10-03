package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Preconditions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One rewrite rule {@code F(p1, ..., pn) == A = B} of a rule module (ADR 0017 §2): the rewriter
 * replaces an instance of {@code pattern} (A) with the same instance of {@code replacement} (B).
 *
 * @param weight the number of selection slots the rule takes when it applies; 0 disables it
 * @param unlabelled the parameters the replacement places where a label of their binding is
 *     invalid, see {@link LabelPlacement}
 */
public record RewriteRule(
        String name,
        int weight,
        TlaEx pattern,
        TlaEx replacement,
        List<RuleParameter> parameters,
        AssignmentFacts assignments,
        Set<String> unlabelled) {
    public RewriteRule {
        Objects.requireNonNull(name, "name");
        Preconditions.requireNonnegative(weight, "weight");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(replacement, "replacement");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(assignments, "assignments");
        unlabelled = Set.copyOf(Objects.requireNonNull(unlabelled, "unlabelled"));
    }

    /** Returns the parameter of this name, if the rule declares one. */
    public Optional<RuleParameter> parameter(String parameterName) {
        return parameters.stream().filter(parameter -> parameter.name().equals(parameterName)).findFirst();
    }

    /** Returns the parameters of one kind, in declaration order. */
    public List<RuleParameter> parameters(RuleParameter.Kind kind) {
        return parameters.stream().filter(parameter -> parameter.kind() == kind).toList();
    }
}
