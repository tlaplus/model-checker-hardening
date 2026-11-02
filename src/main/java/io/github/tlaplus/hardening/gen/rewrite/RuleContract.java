package io.github.tlaplus.hardening.gen.rewrite;

import static org.apalache_mc.tla.jir.TlaOperators.*;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.ValEx;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Reads one rule definition and checks the parts of the rule contract that the loader can decide
 * (ADR 0017 §2, §3, §5, ADR 0027 §1): the shape {@code A = B} or {@code A <=> B}, the parameter
 * classes, level preservation and closedness. It also precomputes what the rule does to the
 * checkers' assignments and what its levels require of a match. Validity in TLA+ is the author's
 * obligation, which the rule module's proofs discharge.
 */
final class RuleContract {
    /**
     * Operators of action level, and ENABLED, which Apalache does not support: a replacement uses
     * one only if its pattern does.
     */
    private static final Set<TlaOper> ACTION = FormulaLevels.with(FormulaLevels.ACTION, ENABLED);

    /**
     * Fairness, which a pattern may apply in place of an action-level operator: it is defined by
     * {@code ENABLED} and {@code <<A>>_v}, so its replacement may apply them.
     */
    private static final Set<TlaOper> FAIRNESS = FormulaLevels.operators(WEAK_FAIRNESS, STRONG_FAIRNESS);

    private RuleContract() {}

    /**
     * Returns the checked rule of {@code family}, or rejects the definition with a message that
     * names the rule.
     */
    static RewriteRule rule(TlaOperDecl definition, Set<String> helpers, int weight, RuleFamily family) {
        var name = definition.name();
        if (definition.isRecursive()) {
            throw rejected(name, "a rule cannot be recursive");
        }
        if (!(definition.body() instanceof OperEx relation) || (relation.oper() != EQ && relation.oper() != EQUIV)) {
            throw rejected(name, "the body must be A = B or A <=> B");
        }
        if (family.temporal() && relation.oper() != EQUIV) {
            throw rejected(name, "the body of a temporal rule must be A <=> B");
        }
        var pattern = TlaExpressions.arguments(relation).get(0);
        var replacement = TlaExpressions.arguments(relation).get(1);
        var temporal = !operators(pattern, FormulaLevels.TEMPORAL).isEmpty()
                || !operators(replacement, FormulaLevels.TEMPORAL).isEmpty();
        if (temporal && !family.temporal()) {
            throw rejected(name, "a temporal rule belongs in metamorphic.temporal_rules (ADR 0027)");
        }
        var declared = TlaDeclarations.parameters(definition);
        var parameterNames = new LinkedHashSet<String>();
        declared.forEach(parameter -> parameterNames.add(parameter.name()));

        var patternReads = IrNames.free(pattern);
        for (var read : patternReads) {
            if (!parameterNames.contains(read)) {
                throw rejected(name, "the pattern reads " + read + ", which is not a parameter");
            }
        }
        var replacementReads = IrNames.free(replacement);
        for (var read : replacementReads) {
            if (!parameterNames.contains(read) && !helpers.contains(read)) {
                throw rejected(name, "the replacement reads " + read + ", which is neither a parameter nor a helper");
            }
        }
        // A rule may trade one action-level operator for another, as UNCHANGED x for x' = x, or
        // expand fairness by ENABLED and <<A>>_v, but must not raise a state-level pattern to an
        // action.
        var raising = operators(replacement, ACTION);
        if (!raising.isEmpty() && operators(pattern, ACTION).isEmpty() && operators(pattern, FAIRNESS).isEmpty()) {
            throw rejected(name, "the replacement applies " + raising.getFirst().name()
                    + ", but the pattern is not an action");
        }

        var parameters = new ArrayList<RuleParameter>();
        for (var parameter : declared) {
            var inPattern = patternReads.contains(parameter.name());
            var inReplacement = replacementReads.contains(parameter.name());
            RuleParameter.Kind kind;
            if (parameter.type() instanceof OperT1) {
                if (!inPattern) {
                    throw rejected(name, "the operator parameter " + parameter.name() + " must occur in the pattern");
                }
                kind = RuleParameter.Kind.HIGHER_ORDER;
            } else if (inPattern) {
                kind = RuleParameter.Kind.MATCHED;
            } else if (inReplacement) {
                kind = RuleParameter.Kind.FRESH;
            } else {
                throw rejected(name, "the parameter " + parameter.name() + " is unused");
            }
            parameters.add(new RuleParameter(parameter.name(), parameter.type(), kind));
        }
        var higherOrder = parameters.stream()
                .filter(parameter -> parameter.kind() == RuleParameter.Kind.HIGHER_ORDER)
                .map(RuleParameter::name)
                .collect(java.util.stream.Collectors.toSet());
        requireMillerPatterns(name, pattern, higherOrder, Set.of());
        requireApplied(name, replacement, higherOrder);
        var signature = new HashSet<Integer>(TlaTypes.usedVariables(TlaTypes.typeOf(pattern)));
        parameters.forEach(parameter -> signature.addAll(TlaTypes.usedVariables(parameter.type())));
        replacement = LetSpecialization.specialize(name, replacement, signature);
        return new RewriteRule(name, weight, TlaExpressions.withArguments(relation, List.of(pattern, replacement)), parameters,
                AssignmentAnalysis.of(pattern, replacement, parameters),
                LabelPlacement.unlabelled(replacement, bound(parameters)),
                LevelAnalysis.of(name, pattern, replacement, parameters, family));
    }


    /** Whether the rule's formula applies no action-level operator, so its instances are state predicates. */
    static boolean isStateLevel(RewriteRule rule) {
        return operators(rule.formula(), ACTION).isEmpty() && operators(rule.formula(), FormulaLevels.TEMPORAL).isEmpty();
    }

    /** Returns the parameters a match binds to subterms of the node: every one but the fresh ones. */
    private static Set<String> bound(List<RuleParameter> parameters) {
        return parameters.stream()
                .filter(parameter -> parameter.kind() != RuleParameter.Kind.FRESH)
                .map(RuleParameter::name)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Requires every occurrence of a higher-order parameter in the pattern to apply it to distinct
     * variables the pattern binds, which makes its match unique.
     */
    private static void requireMillerPatterns(String rule, TlaEx expression, Set<String> higherOrder, Set<String> bound) {
        switch (expression) {
            case NameEx name -> {
                if (higherOrder.contains(name.name())) {
                    throw rejected(rule, "the operator parameter " + name.name() + " must be applied in the pattern");
                }
            }
            case ValEx ignored -> {}
            case LetInEx let -> throw rejected(rule, "a pattern cannot contain LET");
            case OperEx application -> {
                var arguments = TlaExpressions.arguments(application);
                if (application.oper() == OPER_APP && arguments.getFirst() instanceof NameEx head
                        && higherOrder.contains(head.name())) {
                    var applied = new HashSet<String>();
                    for (var argument : arguments.subList(1, arguments.size())) {
                        if (!(argument instanceof NameEx variable) || !bound.contains(variable.name())
                                || !applied.add(variable.name())) {
                            throw rejected(rule, head.name() + " must be applied to distinct variables the pattern binds");
                        }
                    }
                    return;
                }
                var binding = IrBinding.of(application.oper());
                var inner = new HashSet<>(bound);
                IrBinding.boundNames(application).forEach(variable -> inner.add(variable.name()));
                for (var index = 0; index < arguments.size(); index++) {
                    if (!binding.introduces(index)) {
                        requireMillerPatterns(rule, arguments.get(index), higherOrder,
                                binding.scopes(index, arguments.size()) ? inner : bound);
                    }
                }
            }
            default -> throw rejected(rule, "unsupported pattern: " + expression);
        }
    }

    /**
     * Requires the replacement to apply every higher-order parameter. Its binding is a lambda, which
     * instantiation removes only by beta reduction; to pass it on, a rule writes {@code LAMBDA x : P(x)}.
     */
    private static void requireApplied(String rule, TlaEx replacement, Set<String> higherOrder) {
        switch (replacement) {
            case NameEx name -> {
                if (higherOrder.contains(name.name())) {
                    throw rejected(rule, "the operator parameter " + name.name()
                            + " must be applied in the replacement; pass LAMBDA x : " + name.name() + "(x) instead");
                }
            }
            case LetInEx let -> {
                TlaExpressions.localDeclarations(let)
                        .forEach(declaration -> requireApplied(rule, declaration.body(), higherOrder));
                requireApplied(rule, let.body(), higherOrder);
            }
            case OperEx application -> {
                var arguments = TlaExpressions.arguments(application);
                var applied = application.oper() == OPER_APP && arguments.getFirst() instanceof NameEx;
                for (var argument : applied ? arguments.subList(1, arguments.size()) : arguments) {
                    requireApplied(rule, argument, higherOrder);
                }
            }
            default -> {}
        }
    }


    /**
     * Whether a match can bind the parameter to a formula that assigns: a Boolean matched parameter,
     * or a higher-order parameter whose applications are Boolean.
     */
    static boolean carriesFormulas(RuleParameter parameter) {
        return switch (parameter.kind()) {
            case MATCHED -> parameter.type().equals(TlaTypes.BOOL);
            case HIGHER_ORDER -> ((OperT1) parameter.type()).res().equals(TlaTypes.BOOL);
            case FRESH -> false;
        };
    }


    /** Returns the operators of {@code filter} that {@code expression} applies, in first-occurrence order. */
    private static List<TlaOper> operators(TlaEx expression, Set<TlaOper> filter) {
        var found = Collections.newSetFromMap(new IdentityHashMap<TlaOper, Boolean>());
        var order = new ArrayList<TlaOper>();
        TlaExpressions.forEach(expression, node -> {
            if (node instanceof OperEx application && filter.contains(application.oper()) && found.add(application.oper())) {
                order.add(application.oper());
            }
        });
        return order;
    }

    static IllegalArgumentException rejected(String rule, String reason) {
        return new IllegalArgumentException("rewrite rule " + rule + ": " + reason);
    }
}
