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
 * @param formula the temporal formula
 */
public record TemporalProperty(List<TlaEx> fairness, TlaEx formula) {
    public TemporalProperty {
        fairness = List.copyOf(Objects.requireNonNull(fairness, "fairness"));
        Objects.requireNonNull(formula, "formula");
    }

    /** Returns every generated expression: the fairness conditions and then the formula. */
    public List<TlaEx> generated() {
        return Stream.concat(fairness.stream(), Stream.of(formula)).toList();
    }
}
