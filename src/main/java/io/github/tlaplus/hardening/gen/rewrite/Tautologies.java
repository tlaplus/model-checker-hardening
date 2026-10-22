package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.engine.EdgeGuards;
import io.github.tlaplus.hardening.gen.engine.OperandGenerator;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Draws edge guards of a graph module from a rule library (ADR 0023 §6): a guard is the formula
 * {@code A = B} or {@code A <=> B} of a rule, with every parameter drawn at its type. The rule
 * contract makes every instance valid, so a guard holds in every state.
 *
 * <p>A rule can guard an edge when it has a positive weight, its formula applies no action-level
 * operator, it has no operator parameter, which no drawn value could fill, and it reads no helper
 * of the rule module, which the graph module does not define. A guard is a two-byte index over the
 * weighted slots of those rules, in declaration order, as the rewriter selects a rule (ADR 0016
 * §2), followed by the rule's parameters, drawn as the rewriter draws a fresh parameter. A
 * parameter that the formula places where a label is invalid, such as under one of its binders,
 * draws no label.
 */
@ArchRole(Role.FACADE)
@Key
public final class Tautologies implements EdgeGuards {
    private final List<RewriteRule> rules;
    /** The parameters of each rule, by rule name, that draw no label. */
    private final Map<String, Set<String>> unlabelled = new HashMap<>();

    /**
     * @throws IllegalArgumentException if no rule of {@code library} can guard an edge
     */
    public Tautologies(RewriteLibrary library) {
        Objects.requireNonNull(library, "library");
        this.rules = library.rules().stream().filter(Tautologies::guards).toList();
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("no rewrite rule can guard an edge: every rule has weight 0, an"
                    + " action-level operator, an operator parameter or a helper");
        }
        for (var rule : rules) {
            var parameters = new HashSet<String>();
            rule.parameters().forEach(parameter -> parameters.add(parameter.name()));
            unlabelled.put(rule.name(), LabelPlacement.unlabelled(rule.formula(), parameters));
        }
    }

    /** Returns the rules that can guard an edge, in declaration order. */
    public List<RewriteRule> rules() {
        return rules;
    }

    @Override
    public Optional<Generator<TlaEx>> guard(OperandGenerator operands, List<OperandGenerator.Name> scope) {
        Objects.requireNonNull(operands, "operands");
        var visible = List.copyOf(Objects.requireNonNull(scope, "scope"));
        var plans = new ArrayList<RuleOperands.Plan>();
        for (var rule : rules) {
            RuleOperands.planFormula(rule, operands).ifPresent(plans::add);
        }
        var slots = plans.stream().mapToInt(RuleOperands.Plan::weight).sum();
        if (slots == 0) {
            return Optional.empty();
        }
        return Optional.of(draw -> {
            var plan = RuleOperands.select(plans, RuleOperands.Plan::weight, draw.drawIndex(slots, Rewriter.SELECTION_BYTES));
            var drawn = RuleOperands.draw(plan, draw, operands, visible, unlabelled.get(plan.rule().name())::contains);
            return RuleInstantiation.instantiateFormula(
                    plan.rule(), drawn.values(), drawn.types(), RuleOperands.freshNames(operands));
        });
    }

    /** Whether {@code rule} can guard an edge; see the class comment. */
    private static boolean guards(RewriteRule rule) {
        var parameters = new HashSet<String>();
        rule.parameters().forEach(parameter -> parameters.add(parameter.name()));
        return rule.weight() > 0
                && RuleContract.isStateLevel(rule)
                && rule.parameters(RuleParameter.Kind.HIGHER_ORDER).isEmpty()
                && parameters.containsAll(IrNames.free(rule.formula()));
    }
}
