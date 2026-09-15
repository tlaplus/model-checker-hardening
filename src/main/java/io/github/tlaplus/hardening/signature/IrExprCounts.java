package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.ValEx;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The size and constructs of the code the tools evaluate, over the walk that signatures match
 * against.
 *
 * <p>Constructs are named as in Apalache's IR JSON: an operator application by its {@code oper}
 * field, such as {@code SET_ENUM}; a {@code LET-IN} by its {@code kind}, {@code LetInEx}; and a
 * literal by the {@code kind} of its value, such as {@code TlaInt} or {@code TlaBoolSet}. Names
 * are not counted.
 *
 * @param nodes the evaluated subexpressions, labels excluded
 * @param exprs the occurrences of each expression construct, keyed by its name
 */
public record IrExprCounts(long nodes, SortedMap<String, Long> exprs) {
    /** The name of a {@code LET-IN} construct: its {@code kind} in Apalache's IR JSON. */
    public static final String LET_IN = "LetInEx";

    public IrExprCounts {
        exprs = Collections.unmodifiableSortedMap(
                new TreeMap<>(Objects.requireNonNull(exprs, "exprs")));
    }

    /**
     * Counts the subexpressions reachable from the root definitions and their constructs.
     *
     * @throws IllegalArgumentException if the module does not define a root
     */
    public static IrExprCounts evaluated(TlaModule module, List<String> roots) {
        var subexpressions = IrTree.evaluatedSubexpressions(module, roots);
        var exprs = new TreeMap<String, Long>();
        for (var subexpression : subexpressions) {
            var name = switch (subexpression) {
                case OperEx application -> application.oper().name();
                case LetInEx letIn -> LET_IN;
                case ValEx literal -> literalKind(literal);
                default -> null;
            };
            if (name != null) {
                exprs.merge(name, 1L, Long::sum);
            }
        }
        return new IrExprCounts(subexpressions.size(), exprs);
    }

    /**
     * Returns the value class's name, which is the literal's {@code kind} in Apalache's IR JSON. The
     * predefined sets are Scala objects, whose class names end in {@code $}.
     */
    private static String literalKind(ValEx literal) {
        var name = literal.value().getClass().getSimpleName();
        return name.endsWith("$") ? name.substring(0, name.length() - 1) : name;
    }
}
