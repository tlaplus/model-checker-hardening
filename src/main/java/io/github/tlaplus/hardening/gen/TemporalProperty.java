package io.github.tlaplus.hardening.gen;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The temporal property of a generated module and the fairness that constrains it.
 *
 * <p>The parts are kept apart because the checkers are asked about them differently: TLC applies
 * the fairness conditions through the specification, while Apalache, which supports no fairness,
 * checks the implication from fairness to the property. See ADR 0007.
 *
 * @param fairness weak and strong fairness conditions, conjoined in this order
 * @param actionConstraints the {@code [][A]_v} conjuncts, which both checkers accept only at the
 *     top level of a property
 * @param formula the temporal formula conjoined after the action constraints
 */
public record TemporalProperty(List<TlaEx> fairness, List<TlaEx> actionConstraints, TlaEx formula) {
    public TemporalProperty {
        fairness = List.copyOf(Objects.requireNonNull(fairness, "fairness"));
        actionConstraints = List.copyOf(Objects.requireNonNull(actionConstraints, "actionConstraints"));
        Objects.requireNonNull(formula, "formula");
    }

    /** Returns the property's conjuncts: the action constraints, then the formula. */
    public List<TlaEx> conjuncts() {
        return Stream.concat(actionConstraints.stream(), Stream.of(formula)).toList();
    }

    /** Returns every generated expression: the fairness conditions and then the conjuncts. */
    public List<TlaEx> generated() {
        return Stream.concat(fairness.stream(), conjuncts().stream()).toList();
    }
}
