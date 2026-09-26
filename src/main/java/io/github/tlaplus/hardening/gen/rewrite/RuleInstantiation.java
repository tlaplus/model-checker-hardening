package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import io.github.tlaplus.hardening.gen.ir.IrSubstitution;
import io.github.tlaplus.hardening.gen.ir.IrTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;

/**
 * Builds the replacement of a match (ADR 0017 §4): the rule's replacement with its own binders
 * renamed apart, its types instantiated, its parameters substituted and its lambdas applied.
 */
public final class RuleInstantiation {
    private RuleInstantiation() {}

    /**
     * Returns the instantiated replacement.
     *
     * @param fresh the value drawn for each fresh parameter
     * @param types instantiates every type variable of the replacement, extending the match's
     * @param freshName names each variable the replacement binds, apart from every name in scope
     */
    public static TlaEx instantiate(
            RuleMatch match, Map<String, TlaEx> fresh, TlaTypeSubstitution types, UnaryOperator<String> freshName) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(fresh, "fresh");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(freshName, "freshName");
        var rule = match.rule();
        for (var parameter : rule.parameters(RuleParameter.Kind.FRESH)) {
            if (!fresh.containsKey(parameter.name())) {
                throw new IllegalArgumentException("no value for the fresh parameter " + parameter.name());
            }
        }
        var renaming = new HashMap<String, String>();
        IrNames.bound(rule.replacement()).forEach(name -> renaming.put(name, freshName.apply(name)));
        var replacement = IrNames.rename(TlaExpressions.deepCopy(rule.replacement()),
                name -> renaming.getOrDefault(name, name));
        replacement = IrTypes.substitute(replacement, types);
        var values = new HashMap<String, TlaEx>(match.bindings());
        values.putAll(fresh);
        return IrSubstitution.betaReduce(IrSubstitution.substitute(replacement, values));
    }
}
