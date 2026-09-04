package io.github.tlaplus.hardening.gen;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.common.Preconditions;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The declarations one generated module consists of.
 *
 * <p>This is deliberately not a {@code TlaModule}: the surrounding skeleton names the entry points
 * that the tool invocations spell, and that contract belongs beside those invocations rather than
 * beside the decoder. The decoder produces the parts and lets its caller assemble them.
 *
 * <p>Two invariants hold of every value the generator produces, and are what make the module
 * admissible to the parser and both checkers:
 *
 * <ul>
 *   <li>{@code initPredicate} constrains every declared variable, so the initial states are fully
 *       determined.
 *   <li>every disjunct of {@code nextAction} either assigns each declared variable exactly once or
 *       lists it in that disjunct's {@code UNCHANGED}, so a successor state is fully determined.
 * </ul>
 *
 * @param variables the declared state variables, in declaration order
 * @param auxiliaryOperators definitions the predicates may apply, in dependency order
 * @param initPredicate the initial-state predicate
 * @param nextAction the next-state action
 * @param invariant the state invariant
 * @param boundPredicate the state constraint that stops exploration
 * @param stepBound transitions to explore from an initial state, which {@code boundPredicate}
 *     expresses as a constraint for a checker that has no length parameter
 */
public record GeneratedSpec(
        List<TlaVarDecl> variables,
        List<TlaOperDecl> auxiliaryOperators,
        TlaEx initPredicate,
        TlaEx nextAction,
        TlaEx invariant,
        TlaEx boundPredicate,
        int stepBound) {
    public GeneratedSpec {
        variables = List.copyOf(Objects.requireNonNull(variables, "variables"));
        if (variables.isEmpty()) {
            throw new IllegalArgumentException("a module declares at least one variable");
        }
        auxiliaryOperators =
                List.copyOf(Objects.requireNonNull(auxiliaryOperators, "auxiliaryOperators"));
        Objects.requireNonNull(initPredicate, "initPredicate");
        Objects.requireNonNull(nextAction, "nextAction");
        Objects.requireNonNull(invariant, "invariant");
        Objects.requireNonNull(boundPredicate, "boundPredicate");
        Preconditions.requireNonnegative(stepBound, "stepBound");
    }

    /** Returns the generated expressions, each listed once. */
    public List<TlaEx> generated() {
        return Stream.concat(
                        auxiliaryOperators.stream().map(TlaOperDecl::body),
                        Stream.of(initPredicate, nextAction, invariant))
                .toList();
    }
}
