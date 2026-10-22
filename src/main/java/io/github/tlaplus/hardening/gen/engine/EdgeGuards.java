package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.List;
import java.util.Optional;

/**
 * Draws the tautological guards that a graph module conjoins to its edges (ADR 0023 §6). A guard
 * is a state predicate that holds in every state, so it leaves the predicted verdict unchanged,
 * and a checker that evaluates one to {@code FALSE} disables its edge. The rewrite package
 * implements it from a rule library, so this package does not depend on that one.
 */
@ArchRole(Role.CONTRACT)
@FunctionalInterface
public interface EdgeGuards {
    /**
     * Returns a generator of one guard that reads only names of {@code scope} and that draws its
     * operands through {@code operands}, or empty when no guard fits the limits. Choosing between
     * the two draws no byte.
     *
     * @param operands the operand supply of one module, which names every binder apart from it
     */
    Optional<Generator<TlaEx>> guard(OperandGenerator operands, List<OperandGenerator.Name> scope);
}
