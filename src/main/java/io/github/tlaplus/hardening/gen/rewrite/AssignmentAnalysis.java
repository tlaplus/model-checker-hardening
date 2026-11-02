package io.github.tlaplus.hardening.gen.rewrite;

import static org.apalache_mc.tla.jir.TlaOperators.*;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apalache_mc.tla.jir.TlaExpressions;

/**
 * Computes the {@link AssignmentFacts} of a rule (ADR 0017 §5.3): where its Boolean parameters
 * stand in TLC's assignment positions, on each side and in each action (ADR 0027 §1).
 */
final class AssignmentAnalysis {
    /** The single branch of an expression that holds no parameter in an assignment position. */
    private static final Set<Set<String>> NO_PARAMETERS = Set.of(Set.of());

    private AssignmentAnalysis() {}

    /** Precomputes where the Boolean matched parameters end up in the replacement (ADR 0017 §5.3). */
    static AssignmentFacts of(TlaEx pattern, TlaEx replacement, List<RuleParameter> parameters) {
        var booleans = parameters.stream()
                .filter(RuleContract::carriesFormulas)
                .map(RuleParameter::name)
                .collect(Collectors.toSet());
        // A formula that assigned in the pattern must still assign, once; one that did not must not
        // start to, or a guard such as an IF condition would become an assignment. Each branch of
        // the replacement must hold the formulas of a branch of the pattern, for Apalache.
        var preserved = assigningOnce(replacement, booleans);
        preserved.retainAll(assigningOnce(pattern, booleans));
        return new AssignmentFacts(preserved, side(pattern, booleans), side(replacement, booleans),
                operands(replacement, PRIME), operands(replacement, UNCHANGED));
    }

    private static AssignmentFacts.Side side(TlaEx expression, Set<String> parameters) {
        return new AssignmentFacts.Side(firstOccurrences(expression, parameters), branches(expression, parameters));
    }

    /**
     * Returns, for each branch through the disjunctions and {@code IF} branches of {@code expression},
     * the parameters it holds in an assignment position. A conjunction, an {@code \E} body, a label
     * and a {@code LET} body hold one branch of each of their assigning arguments.
     */
    private static Set<Set<String>> branches(TlaEx expression, Set<String> parameters) {
        return switch (expression) {
            case NameEx name -> parameters.contains(name.name()) ? Set.of(Set.of(name.name())) : NO_PARAMETERS;
            case LetInEx let -> branches(let.body(), parameters);
            case OperEx application -> {
                var operator = application.oper();
                var alternatives = operator == OR || operator == IF_THEN_ELSE;
                var arguments = TlaExpressions.arguments(application);
                Set<Set<String>> result = alternatives ? new HashSet<>() : NO_PARAMETERS;
                for (var index = 0; index < arguments.size(); index++) {
                    if (keepsAssignments(operator, index)) {
                        var argument = branches(arguments.get(index), parameters);
                        result = alternatives ? union(result, argument) : product(result, argument);
                    }
                }
                yield result;
            }
            default -> NO_PARAMETERS;
        };
    }

    private static Set<Set<String>> union(Set<Set<String>> left, Set<Set<String>> right) {
        var result = new HashSet<>(left);
        result.addAll(right);
        return result;
    }

    /** Returns every union of one branch of {@code left} with one branch of {@code right}. */
    private static Set<Set<String>> product(Set<Set<String>> left, Set<Set<String>> right) {
        var result = new HashSet<Set<String>>();
        for (var first : left) {
            for (var second : right) {
                var branch = new HashSet<>(first);
                branch.addAll(second);
                result.add(branch);
            }
        }
        return result;
    }

    /**
     * Returns the parameters that occur in {@code expression} only in assigning positions, at most
     * once in each action. The action of {@code [A]_v}, {@code <<A>>_v}, fairness or {@code ENABLED}
     * is an action of its own, which TLC evaluates apart from the formula around it; a rule without
     * one has one action, so its parameters occur exactly once.
     */
    private static Set<String> assigningOnce(TlaEx expression, Set<String> parameters) {
        var occurrences = new HashMap<String, List<Occurrence>>();
        collectOccurrences(expression, new Occurrence(0, true), new int[] {0}, parameters, occurrences);
        var once = new HashSet<String>();
        occurrences.forEach((parameter, positions) -> {
            var actions = positions.stream().map(Occurrence::action).distinct().count();
            if (actions == positions.size() && positions.stream().allMatch(Occurrence::assigning)) {
                once.add(parameter);
            }
        });
        return once;
    }

    /** Where a parameter occurs: in which action, and whether TLC would assign an equation there. */
    private record Occurrence(int action, boolean assigning) {}

    /** Returns the parameters {@code operator} applies to directly in {@code expression}. */
    private static Set<String> operands(TlaEx expression, TlaOper operator) {
        var result = new HashSet<String>();
        TlaExpressions.forEach(expression, node -> {
            if (node instanceof OperEx application && application.oper() == operator
                    && TlaExpressions.arguments(application).getFirst() instanceof NameEx name) {
                result.add(name.name());
            }
        });
        return result;
    }

    /**
     * Records, for each occurrence of a parameter, its action and whether TLC would treat an
     * equation there as an assignment. An action position starts a new action, numbered by {@code
     * actions}, where TLC assigns again.
     */
    private static void collectOccurrences(TlaEx expression, Occurrence where, int[] actions, Set<String> parameters,
            Map<String, List<Occurrence>> occurrences) {
        switch (expression) {
            case NameEx name -> {
                if (parameters.contains(name.name())) {
                    occurrences.computeIfAbsent(name.name(), ignored -> new ArrayList<>()).add(where);
                }
            }
            case LetInEx let -> {
                TlaExpressions.localDeclarations(let).forEach(declaration -> collectOccurrences(
                        declaration.body(), new Occurrence(where.action(), false), actions, parameters, occurrences));
                collectOccurrences(let.body(), where, actions, parameters, occurrences);
            }
            case OperEx application -> {
                var arguments = TlaExpressions.arguments(application);
                for (var index = 0; index < arguments.size(); index++) {
                    var operand = FormulaLevels.isActionPosition(application.oper(), index)
                            ? new Occurrence(++actions[0], true)
                            : new Occurrence(where.action(), where.assigning() && keepsAssignments(application.oper(), index));
                    collectOccurrences(arguments.get(index), operand, actions, parameters, occurrences);
                }
            }
            default -> {}
        }
    }

    /** Whether TLC treats an equation in argument {@code index} of {@code operator} as an assignment. */
    private static boolean keepsAssignments(TlaOper operator, int index) {
        if (operator == AND || operator == OR) {
            return true;
        }
        if (operator == OPER_APP) {
            // The applied parameter stands for its application, where the lambda body lands.
            return index == 0;
        }
        if (operator == IF_THEN_ELSE) {
            return index > 0;
        }
        if (operator == EXISTS3) {
            return index == 2;
        }
        if (operator == EXISTS2) {
            return index == 1;
        }
        return operator == LABEL && index == 0;
    }

    private static List<String> firstOccurrences(TlaEx expression, Set<String> parameters) {
        var order = new LinkedHashSet<String>();
        TlaExpressions.forEach(expression, node -> {
            if (node instanceof NameEx name && parameters.contains(name.name())) {
                order.add(name.name());
            }
        });
        return List.copyOf(order);
    }
}
