package io.github.tlaplus.hardening.gen.rewrite;

import java.util.Objects;
import java.util.Set;

/**
 * What the levels of a temporal rule require of a match (ADR 0027 §1).
 *
 * @param formulaParameters the Boolean matched parameters that occur in the pattern outside every
 *     action position; a match binds them only to state predicates and temporal formulas, never to
 *     an action. Empty for a rule of {@link RuleFamily#REWRITES}, whose Boolean parameters bind
 *     actions such as {@code x' = 1}.
 * @param raisesLevel whether the replacement applies a temporal operator and the pattern applies
 *     none, so the rule applies only where a temporal formula may occur
 */
public record RuleLevels(Set<String> formulaParameters, boolean raisesLevel) {
    /** The levels of a rule without temporal operators. */
    public static final RuleLevels NONE = new RuleLevels(Set.of(), false);

    public RuleLevels {
        formulaParameters = Set.copyOf(Objects.requireNonNull(formulaParameters, "formulaParameters"));
    }
}
