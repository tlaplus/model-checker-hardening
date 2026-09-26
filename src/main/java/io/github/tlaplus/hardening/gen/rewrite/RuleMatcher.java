package io.github.tlaplus.hardening.gen.rewrite;

import static org.apalache_mc.tla.jir.TlaOperators.OPER_APP;
import static org.apalache_mc.tla.jir.TlaOperators.PRIME;
import static org.apalache_mc.tla.jir.TlaOperators.UNCHANGED;

import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import at.forsyte.apalache.tla.lir.ValEx;
import io.github.tlaplus.hardening.gen.ir.IrAlpha;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;
import org.apalache_mc.tla.jir.TlaTypeUnifier;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.apalache_mc.tla.jir.TypedParameter;

/**
 * Matches a rule's pattern at one node (ADR 0017 §4). Matching reads no bytes.
 *
 * <ul>
 *   <li>A literal and an operator match themselves; a binder matches a binder of the same form,
 *       and its variables are identified with the node's.
 *   <li>A matched parameter binds a subterm that reads no variable the pattern binds; a repeated
 *       parameter requires alpha-equivalent subterms.
 *   <li>A higher-order parameter applied to bound variables binds a lambda over those variables,
 *       whose body may read no other variable the pattern binds.
 *   <li>Every binding unifies the parameter's type with the subterm's, so a polymorphic rule applies
 *       at every type it admits.
 * </ul>
 *
 * <p>A match at a Boolean node is refused when it would move an assigning equation out of a
 * position where TLC treats it as an assignment (ADR 0017 §5.3).
 */
public final class RuleMatcher {
    /** The name of every lambda a match builds; instantiation beta-reduces it away. */
    static final String LAMBDA = "RewriteLambda";

    private RuleMatcher() {}

    /** Returns the match of {@code rule} at {@code node}, if its pattern matches and the match is admissible. */
    public static Optional<RuleMatch> match(RewriteRule rule, TlaEx node) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(node, "node");
        var state = new State(rule);
        if (!state.unify(TlaTypes.typeOf(rule.pattern()), TlaTypes.typeOf(node))
                || !state.match(rule.pattern(), node, Map.of())) {
            return Optional.empty();
        }
        var match = new RuleMatch(rule, state.bindings, state.types.orElse(TlaTypeSubstitution.empty()));
        return admitsAssignments(match, node) ? Optional.of(match) : Optional.empty();
    }

    /**
     * Whether a match keeps TLC's assignments. At a Boolean node, every binding that reads a primed
     * variable must be a formula the rule keeps in an assigning position, in the same order.
     */
    private static boolean admitsAssignments(RuleMatch match, TlaEx node) {
        if (!TlaTypes.typeOf(node).equals(TlaTypes.BOOL)) {
            return true;
        }
        var primed = new HashSet<String>();
        for (var binding : match.bindings().entrySet()) {
            if (!readsNextState(binding.getValue())) {
                continue;
            }
            var parameter = match.rule().parameter(binding.getKey()).orElseThrow();
            if (!RuleContract.carriesFormulas(parameter)) {
                return false;
            }
            primed.add(parameter.name());
        }
        return match.rule().assignments().admits(primed);
    }

    private static boolean readsNextState(TlaEx expression) {
        var found = new boolean[1];
        TlaExpressions.forEach(expression, node -> {
            if (node instanceof OperEx application && (application.oper() == PRIME || application.oper() == UNCHANGED)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /** The bindings and type substitution a match accumulates. */
    private static final class State {
        private final RewriteRule rule;
        private final TlaTypeUnifier unifier;
        private final Map<String, TlaEx> bindings = new HashMap<>();
        private Optional<TlaTypeSubstitution> types = Optional.empty();

        State(RewriteRule rule) {
            this.rule = rule;
            var reserved = new ArrayList<TlaType1>();
            rule.parameters().forEach(parameter -> reserved.add(parameter.type()));
            reserved.add(TlaTypes.typeOf(rule.pattern()));
            unifier = new TlaTypeUnifier(reserved.toArray(TlaType1[]::new));
        }

        /** Unifies a rule type with a node type, extending the substitution. */
        boolean unify(TlaType1 ruleType, TlaType1 nodeType) {
            var unification = unifier.unify(types, ruleType, nodeType);
            if (unification.isEmpty()) {
                return false;
            }
            types = Optional.of(unification.get().substitution());
            return true;
        }

        /**
         * Matches {@code pattern} against {@code node}; {@code bound} maps each variable the
         * pattern has bound so far to the node's variable at the same place.
         */
        boolean match(TlaEx pattern, TlaEx node, Map<String, String> bound) {
            return switch (pattern) {
                case NameEx name -> matchName(name, node, bound);
                case ValEx value -> node instanceof ValEx other && value.equals(other);
                case OperEx application -> isHigherOrderApplication(application)
                        ? matchHigherOrder(application, node, bound)
                        : matchApplication(application, node, bound);
                default -> false;
            };
        }

        private boolean matchName(NameEx name, TlaEx node, Map<String, String> bound) {
            var variable = bound.get(name.name());
            if (variable != null) {
                return node instanceof NameEx other && other.name().equals(variable);
            }
            var parameter = rule.parameter(name.name()).orElseThrow();
            if (!unify(parameter.type(), TlaTypes.typeOf(node)) || readsAny(node, Set.copyOf(bound.values()))) {
                return false;
            }
            return bind(parameter.name(), node);
        }

        private boolean matchApplication(OperEx application, TlaEx node, Map<String, String> bound) {
            if (!(node instanceof OperEx other) || other.oper() != application.oper()) {
                return false;
            }
            var patternArguments = TlaExpressions.arguments(application);
            var nodeArguments = TlaExpressions.arguments(other);
            if (patternArguments.size() != nodeArguments.size()) {
                return false;
            }
            var binding = IrBinding.of(application.oper());
            var inner = new HashMap<>(bound);
            for (var index = 0; index < patternArguments.size(); index++) {
                if (!binding.introduces(index)) {
                    continue;
                }
                var patternNames = IrBinding.names(patternArguments.get(index));
                var nodeNames = IrBinding.names(nodeArguments.get(index));
                if (patternNames.size() != nodeNames.size()) {
                    return false;
                }
                for (var position = 0; position < patternNames.size(); position++) {
                    if (!unify(TlaTypes.typeOf(patternNames.get(position)), TlaTypes.typeOf(nodeNames.get(position)))) {
                        return false;
                    }
                    inner.put(patternNames.get(position).name(), nodeNames.get(position).name());
                }
            }
            for (var index = 0; index < patternArguments.size(); index++) {
                if (!binding.introduces(index)
                        && !match(patternArguments.get(index), nodeArguments.get(index),
                                binding.scopes(index, patternArguments.size()) ? inner : bound)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isHigherOrderApplication(OperEx application) {
            return application.oper() == OPER_APP
                    && TlaExpressions.arguments(application).getFirst() instanceof NameEx head
                    && rule.parameter(head.name()).map(parameter -> parameter.kind() == RuleParameter.Kind.HIGHER_ORDER)
                            .orElse(false);
        }

        /** Abstracts the node over the node variables the parameter is applied to. */
        private boolean matchHigherOrder(OperEx application, TlaEx node, Map<String, String> bound) {
            var arguments = TlaExpressions.arguments(application);
            var parameter = rule.parameter(((NameEx) arguments.getFirst()).name()).orElseThrow();
            var applied = arguments.subList(1, arguments.size());
            var abstracted = new HashSet<String>();
            var argumentTypes = new ArrayList<TlaType1>();
            for (var argument : applied) {
                abstracted.add(bound.get(((NameEx) argument).name()));
                argumentTypes.add(TlaTypes.typeOf(argument));
            }
            var others = new HashSet<>(bound.values());
            others.removeAll(abstracted);
            if (readsAny(node, others)
                    || !unify(parameter.type(), TlaTypes.operator(TlaTypes.typeOf(node), argumentTypes.toArray(TlaType1[]::new)))) {
                return false;
            }
            var substitution = types.orElse(TlaTypeSubstitution.empty());
            var parameters = new ArrayList<TypedParameter>();
            for (var argument : applied) {
                parameters.add(new TypedParameter(bound.get(((NameEx) argument).name()),
                        substitution.applyFully(TlaTypes.typeOf(argument))));
            }
            var lambda = new TlaTypedScopeUncheckedBuilder().lambda(
                    LAMBDA, TlaExpressions.deepCopy(node), parameters.toArray(TypedParameter[]::new));
            return bind(parameter.name(), lambda);
        }

        /** Binds a parameter, or requires an equivalent value when the pattern repeats it. */
        private boolean bind(String parameter, TlaEx value) {
            var previous = bindings.putIfAbsent(parameter, value);
            return previous == null || IrAlpha.equivalent(previous, value);
        }

        private static boolean readsAny(TlaEx expression, Set<String> names) {
            if (names.isEmpty()) {
                return false;
            }
            for (var read : IrNames.free(expression)) {
                if (names.contains(read)) {
                    return true;
                }
            }
            return false;
        }
    }
}
