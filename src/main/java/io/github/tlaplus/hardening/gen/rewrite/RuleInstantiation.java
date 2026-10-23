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
 * Builds the instance of a rule (ADR 0017 §4): the rule's replacement for a match, or its whole
 * formula for an edge guard (ADR 0023 §6). The instantiated part has its own binders renamed
 * apart, its types instantiated, its parameters substituted and its lambdas applied.
 */
public final class RuleInstantiation {
    private static final String LABEL_PREFIX = "label";

    private RuleInstantiation() {}

    /**
     * Returns the instantiated replacement.
     *
     * @param fresh the value drawn for each fresh parameter
     * @param types instantiates every type variable of the replacement, extending the match's
     * @param freshName names each variable the replacement binds, and each label and LET definition
     *     a copied value repeats, apart from every name in scope
     */
    public static TlaEx instantiate(
            RuleMatch match, Map<String, TlaEx> fresh, TlaTypeSubstitution types, UnaryOperator<String> freshName) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(fresh, "fresh");
        var rule = match.rule();
        for (var parameter : rule.parameters(RuleParameter.Kind.FRESH)) {
            if (!fresh.containsKey(parameter.name())) {
                throw new IllegalArgumentException("no value for the fresh parameter " + parameter.name());
            }
        }
        var values = new HashMap<String, TlaEx>(match.bindings());
        values.putAll(fresh);
        return instantiate(rule.replacement(), values, types, freshName);
    }

    /**
     * Returns the instantiated formula {@code A = B} or {@code A <=> B} of a rule without
     * higher-order parameters, whose every parameter takes a drawn value.
     *
     * @param values the value of each parameter
     * @param types instantiates every type variable of the formula
     * @param freshName names each variable the formula binds, and each label and LET definition a
     *     value repeats, apart from every name in scope
     */
    public static TlaEx instantiateFormula(
            RewriteRule rule, Map<String, TlaEx> values, TlaTypeSubstitution types, UnaryOperator<String> freshName) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(values, "values");
        for (var parameter : rule.parameters()) {
            if (parameter.kind() == RuleParameter.Kind.HIGHER_ORDER) {
                throw new IllegalArgumentException("the formula of " + rule.name() + " has an operator parameter");
            }
            if (!values.containsKey(parameter.name())) {
                throw new IllegalArgumentException("no value for the parameter " + parameter.name());
            }
        }
        return instantiate(rule.formula(), values, types, freshName);
    }

    private static TlaEx instantiate(
            TlaEx template, Map<String, TlaEx> values, TlaTypeSubstitution types, UnaryOperator<String> freshName) {
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(freshName, "freshName");
        var renaming = new HashMap<String, String>();
        IrNames.bound(template).forEach(name -> renaming.put(name, freshName.apply(name)));
        var instance = IrNames.rename(TlaExpressions.deepCopy(template), name -> renaming.getOrDefault(name, name));
        instance = IrTypes.substitute(instance, types);
        // A parameter that occurs twice copies the labels and LET definitions of its value.
        var copied = IrSubstitution.betaReduce(IrSubstitution.substitute(instance, values));
        return IrNames.redefineRepeats(IrNames.relabelRepeats(copied, () -> freshName.apply(LABEL_PREFIX)), freshName);
    }
}
