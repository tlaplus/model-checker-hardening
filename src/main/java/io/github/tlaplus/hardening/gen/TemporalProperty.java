package io.github.tlaplus.hardening.gen;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The temporal property of a generated module and the fairness that constrains it.
 *
 * <p>A fairness condition could as well occur inside the formula, as in {@code WF_v(A) => <>P}.
 * The conditions are kept apart to generate the idiom specifications use, fairness as conjuncts of
 * {@code Spec == Init /\ [][Next]_vars /\ Fairness}, which puts them in a different definition
 * from the formula.
 *
 * <p>The split also compensates for Apalache's partial support of temporal properties. Apalache
 * checks {@code Init} and {@code Next} rather than {@code Spec} and supports no fairness, so it is
 * asked {@code Fairness => Prop}, the implication TLC answers under {@code Spec}. A module with
 * fairness then makes Apalache fail visibly instead of reporting a counterexample the fairness
 * excludes. See ADR 0007.
 *
 * <p>Fairness exists only with a property, because both are decoded from the property section; a
 * record under {@code Optional} states that, and keeps {@code GeneratedSpec} at six components.
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
