package io.github.tlaplus.hardening.gen.rewrite;

import java.util.Objects;
import java.util.Set;

/**
 * What the levels of a rule require of a match (ADR 0027 §1).
 *
 * @param formulaParameters the Boolean matched parameters of a temporal rule that occur in the
 *     pattern outside every action position; a match binds them only to state predicates and
 *     temporal formulas, never to an action. Empty for a rule of {@link RuleFamily#REWRITES}, whose
 *     Boolean parameters bind actions such as {@code x' = 1}.
 * @param raisesLevel whether the replacement applies a temporal operator and the pattern applies
 *     none, so the rule applies only where a temporal formula may occur
 * @param formulaPositions the Boolean parameters, matched or higher-order, that the replacement
 *     holds in formula positions only: a match may bind them to a temporal formula, which SANY
 *     rejects under {@code =}, in a tuple, a set or a {@code CHOOSE}
 */
public record RuleLevels(Set<String> formulaParameters, boolean raisesLevel, Set<String> formulaPositions) {
    public RuleLevels {
        formulaParameters = Set.copyOf(Objects.requireNonNull(formulaParameters, "formulaParameters"));
        formulaPositions = Set.copyOf(Objects.requireNonNull(formulaPositions, "formulaPositions"));
    }
}
