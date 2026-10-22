package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.engine.OperandGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;
import org.apalache_mc.tla.jir.TlaTypeUnifier;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Draws what instantiating a rule needs beyond a match (ADR 0017 §3): a concrete type for every
 * type variable the match leaves open, then a value for each drawn parameter at its type. The
 * rewriter draws the fresh parameters of a match; an edge guard draws every parameter of its rule
 * (ADR 0023 §6).
 */
final class RuleOperands {
    private RuleOperands() {}

    /**
     * A rule whose drawn parameters can be drawn within the limits.
     *
     * @param drawn the parameters to draw, in declaration order
     * @param open the type variables the match leaves open, when there are any
     */
    record Plan(RuleMatch match, List<RuleParameter> drawn, Optional<OpenTypes> open) {
        Plan {
            Objects.requireNonNull(match, "match");
            drawn = List.copyOf(Objects.requireNonNull(drawn, "drawn"));
            Objects.requireNonNull(open, "open");
        }

        RewriteRule rule() {
            return match.rule();
        }

        int weight() {
            return match.rule().weight();
        }
    }

    /**
     * The type variables a match leaves open.
     *
     * @param template an operator type over the open variables
     * @param instantiation draws a concrete instance of {@code template}
     */
    record OpenTypes(TlaType1 template, Generator<TlaType1> instantiation) {}

    /** The drawn values, and the substitution that instantiates every type variable they were drawn at. */
    record Drawn(Map<String, TlaEx> values, TlaTypeSubstitution types) {}

    /**
     * Plans drawing the fresh parameters of {@code match}. Empty when the type variables the match
     * leaves open in them and in the replacement have no instance within the limits. Draws no byte.
     */
    static Optional<Plan> planFresh(RuleMatch match, OperandGenerator operands) {
        var rule = match.rule();
        var drawn = rule.parameters(RuleParameter.Kind.FRESH);
        var open = new LinkedHashSet<Integer>();
        for (var parameter : drawn) {
            open.addAll(TlaTypes.usedVariables(match.types().applyFully(parameter.type())));
        }
        TlaExpressions.forEach(rule.replacement(),
                node -> open.addAll(TlaTypes.usedVariables(match.types().applyFully(TlaTypes.typeOf(node)))));
        var template = open.stream().<TlaType1>map(TlaTypes::typeVariable).toList();
        return plan(match, drawn, template, operands);
    }

    /**
     * Plans drawing every parameter of {@code rule}, whose whole formula is instantiated. The
     * template of the open type variables is made of the types that hold them, the parameters'
     * first, so a row variable is instantiated as a row. Empty when they have no instance within
     * the limits. Draws no byte.
     */
    static Optional<Plan> planFormula(RewriteRule rule, OperandGenerator operands) {
        var match = new RuleMatch(rule, Map.of(), TlaTypeSubstitution.empty());
        var template = new ArrayList<TlaType1>();
        var covered = new LinkedHashSet<Integer>();
        for (var parameter : rule.parameters()) {
            var used = TlaTypes.usedVariables(parameter.type());
            if (!used.isEmpty()) {
                template.add(parameter.type());
                covered.addAll(used);
            }
        }
        // A variable no parameter mentions, such as the row of Variant("Tag0", x), enters with the
        // type of the first subterm that mentions it, which keeps a row in a row position.
        TlaExpressions.forEach(rule.formula(), node -> {
            var type = TlaTypes.typeOf(node);
            var used = TlaTypes.usedVariables(type);
            if (!covered.containsAll(used)) {
                template.add(type);
                covered.addAll(used);
            }
        });
        return plan(match, rule.parameters(), template, operands);
    }

    /** Plans drawing {@code drawn}, with the open type variables those of {@code template}. */
    private static Optional<Plan> plan(
            RuleMatch match, List<RuleParameter> drawn, List<TlaType1> template, OperandGenerator operands) {
        if (template.isEmpty()) {
            return Optional.of(new Plan(match, drawn, Optional.empty()));
        }
        var operator = TlaTypes.operator(TlaTypes.BOOL, template.toArray(TlaType1[]::new));
        return operands.instantiate(operator)
                .map(instantiation -> new Plan(match, drawn, Optional.of(new OpenTypes(operator, instantiation))));
    }

    /**
     * Draws the open type variables of {@code plan}, then each drawn parameter in declaration order,
     * in {@code scope} and at the state level.
     *
     * @param unlabelled the parameters whose value may hold no label
     */
    static Drawn draw(Plan plan, Draw draw, OperandGenerator operands, List<OperandGenerator.Name> scope,
            Predicate<String> unlabelled) {
        var types = plan.match().types();
        if (plan.open().isPresent()) {
            var open = plan.open().get();
            var concrete = draw.draw(open.instantiation());
            types = new TlaTypeUnifier(open.template())
                    .unify(Optional.of(types), open.template(), concrete)
                    .orElseThrow(() -> new IllegalStateException("instantiation does not unify: " + concrete))
                    .substitution();
        }
        var values = new LinkedHashMap<String, TlaEx>();
        for (var parameter : plan.drawn()) {
            values.put(parameter.name(), draw.draw(operands.operand(
                    types.applyFully(parameter.type()), scope, unlabelled.test(parameter.name()))));
        }
        return new Drawn(values, types);
    }

    /** Returns the option whose weighted slots, in list order, contain {@code slot}. */
    static <T> T select(List<T> options, ToIntFunction<T> weight, int slot) {
        var remaining = slot;
        for (var option : options) {
            if (remaining < weight.applyAsInt(option)) {
                return option;
            }
            remaining -= weight.applyAsInt(option);
        }
        throw new IllegalStateException("slot " + slot + " exceeds the weights of the options");
    }

    /** Names a binder, label or LET definition of an instance apart from every name in use. */
    static UnaryOperator<String> freshNames(OperandGenerator operands) {
        return name -> operands.freshName(name.replaceAll("\\d+$", ""));
    }
}
