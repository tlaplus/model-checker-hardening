package io.github.tlaplus.hardening.gen.rewrite;

import static org.apalache_mc.tla.jir.TlaOperators.OPER_APP;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;

/** Computes the {@link RuleLevels} of a rule as its loader checks it (ADR 0027 §1). */
final class LevelAnalysis {
    private LevelAnalysis() {}

    /**
     * Classifies the Boolean matched parameters of a temporal rule by their positions in the
     * pattern (ADR 0027 §1): one in an action position matches an action, every other one a state
     * predicate or a temporal formula. A parameter in both kinds of position is rejected. For every
     * rule, it also records which Boolean parameters the replacement holds in formula positions.
     */
    static RuleLevels of(
            String rule, TlaEx pattern, TlaEx replacement, List<RuleParameter> parameters, RuleFamily family) {
        var formulaPositions = formulaPositions(replacement, parameters);
        if (!family.temporal()) {
            return new RuleLevels(Set.of(), false, formulaPositions);
        }
        var actions = new HashSet<String>();
        var formulas = new HashSet<String>();
        classify(pattern, false, actions, formulas);
        var formulaParameters = new HashSet<String>();
        for (var parameter : parameters) {
            if (parameter.kind() != RuleParameter.Kind.MATCHED || !parameter.type().equals(TlaTypes.BOOL)) {
                continue;
            }
            if (actions.contains(parameter.name()) && formulas.contains(parameter.name())) {
                throw RuleContract.rejected(rule, "the parameter " + parameter.name()
                        + " occurs in the pattern both as an action and as a formula");
            }
            if (!actions.contains(parameter.name())) {
                formulaParameters.add(parameter.name());
            }
        }
        var raises = !FormulaLevels.isTemporal(pattern) && FormulaLevels.isTemporal(replacement);
        return new RuleLevels(formulaParameters, raises, formulaPositions);
    }

    /**
     * Returns the Boolean parameters that every occurrence in {@code replacement} holds in a formula
     * position, reached from the root through operands that pass a temporal formula on. An applied
     * higher-order parameter stands where its application stands; its arguments are values.
     */
    private static Set<String> formulaPositions(TlaEx replacement, List<RuleParameter> parameters) {
        var booleans = parameters.stream()
                .filter(RuleContract::carriesFormulas)
                .map(RuleParameter::name)
                .collect(java.util.stream.Collectors.toSet());
        var outside = new HashSet<String>();
        collectOutsideFormulas(replacement, true, booleans, outside);
        var result = new HashSet<>(booleans);
        result.removeAll(outside);
        return result;
    }

    /** Collects the parameters of {@code booleans} that {@code expression} holds outside a formula position. */
    private static void collectOutsideFormulas(TlaEx expression, boolean formula, Set<String> booleans, Set<String> outside) {
        switch (expression) {
            case NameEx name -> {
                if (!formula && booleans.contains(name.name())) {
                    outside.add(name.name());
                }
            }
            case LetInEx let -> {
                TlaExpressions.localDeclarations(let)
                        .forEach(declaration -> collectOutsideFormulas(declaration.body(), false, booleans, outside));
                collectOutsideFormulas(let.body(), formula, booleans, outside);
            }
            case OperEx application -> {
                var arguments = TlaExpressions.arguments(application);
                var applied = application.oper() == OPER_APP && arguments.getFirst() instanceof NameEx head
                        && booleans.contains(head.name());
                for (var index = 0; index < arguments.size(); index++) {
                    var operand = applied
                            ? index == 0 && formula
                            : formula && FormulaLevels.passesFormula(application.oper(), index, arguments.size());
                    collectOutsideFormulas(arguments.get(index), operand, booleans, outside);
                }
            }
            default -> {}
        }
    }

    /** Collects the names {@code expression} reads in action positions and elsewhere. */
    private static void classify(TlaEx expression, boolean action, Set<String> actions, Set<String> formulas) {
        switch (expression) {
            case NameEx name -> (action ? actions : formulas).add(name.name());
            case LetInEx let -> {
                TlaExpressions.localDeclarations(let)
                        .forEach(declaration -> classify(declaration.body(), action, actions, formulas));
                classify(let.body(), action, actions, formulas);
            }
            case OperEx application -> {
                var arguments = TlaExpressions.arguments(application);
                for (var index = 0; index < arguments.size(); index++) {
                    classify(arguments.get(index), action || FormulaLevels.isActionPosition(application.oper(), index),
                            actions, formulas);
                }
            }
            default -> {}
        }
    }
}
