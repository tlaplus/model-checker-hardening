package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.Map;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;

/**
 * A rule's pattern matched at one node.
 *
 * @param bindings the subterm of each matched parameter and the lambda of each higher-order one
 * @param types instantiates the rule's type variables as far as the match determines them
 */
public record RuleMatch(RewriteRule rule, Map<String, TlaEx> bindings, TlaTypeSubstitution types) {
    public RuleMatch {
        Objects.requireNonNull(rule, "rule");
        bindings = Map.copyOf(Objects.requireNonNull(bindings, "bindings"));
        Objects.requireNonNull(types, "types");
    }
}
