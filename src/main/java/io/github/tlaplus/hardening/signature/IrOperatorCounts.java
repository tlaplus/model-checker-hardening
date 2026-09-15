package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The size and operators of the code the tools evaluate, over the walk that signatures match
 * against.
 *
 * @param nodes the evaluated subexpressions, labels excluded
 * @param operators the applications of each operator, keyed by its name in Apalache's IR
 */
public record IrOperatorCounts(long nodes, SortedMap<String, Long> operators) {
    public IrOperatorCounts {
        operators = Collections.unmodifiableSortedMap(
                new TreeMap<>(Objects.requireNonNull(operators, "operators")));
    }

    /**
     * Counts the subexpressions reachable from the root definitions and their operators.
     *
     * @throws IllegalArgumentException if the module does not define a root
     */
    public static IrOperatorCounts evaluated(TlaModule module, List<String> roots) {
        var subexpressions = IrTree.evaluatedSubexpressions(module, roots);
        var operators = new TreeMap<String, Long>();
        for (var subexpression : subexpressions) {
            if (subexpression instanceof OperEx application) {
                operators.merge(application.oper().name(), 1L, Long::sum);
            }
        }
        return new IrOperatorCounts(subexpressions.size(), operators);
    }
}
