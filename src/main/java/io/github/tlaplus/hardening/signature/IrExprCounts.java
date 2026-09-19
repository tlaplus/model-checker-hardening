package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.ValEx;
import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.common.ExprEdge;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Counts the size and constructs of the code the tools evaluate, over the walk that signatures match
 * against.
 *
 * <p>Constructs are named as in Apalache's IR JSON: an operator application by its {@code oper}
 * field, such as {@code SET_ENUM}; a {@code LET-IN} by its {@code kind}, {@code LetInEx}; and a
 * literal by the {@code kind} of its value, such as {@code TlaInt} or {@code TlaBoolSet}. Names
 * are not counted.
 *
 * <p>An edge joins a construct to a construct that is its immediate subexpression: an argument of
 * an operator application, or the body of a {@code LET-IN}. A name is not a construct, so the body
 * of a definition reached through a name starts a tree of its own.
 */
public final class IrExprCounts {
    /** The name of a {@code LET-IN} construct: its {@code kind} in Apalache's IR JSON. */
    public static final String LET_IN = "LetInEx";

    private IrExprCounts() {}

    /**
     * Counts the subexpressions reachable from the root definitions, labels excluded, their
     * constructs, and the edges between constructs.
     *
     * @throws IllegalArgumentException if the module does not define a root
     */
    public static ExprCounts evaluated(TlaModule module, List<String> roots) {
        var visits = IrTree.evaluatedVisits(module, roots);
        var exprs = new TreeMap<String, Long>();
        var edges = new TreeMap<ExprEdge, Long>();
        for (var visit : visits) {
            construct(visit.node()).ifPresent(child -> {
                exprs.merge(child, 1L, Long::sum);
                visit.parent()
                        .flatMap(IrExprCounts::construct)
                        .ifPresent(parent -> edges.merge(new ExprEdge(parent, child), 1L, Long::sum));
            });
        }
        return new ExprCounts(visits.size(), exprs, edges);
    }

    /** Returns the construct name of an expression, or nothing for a name. */
    private static Optional<String> construct(TlaEx expression) {
        return Optional.ofNullable(switch (expression) {
            case OperEx application -> application.oper().name();
            case LetInEx letIn -> LET_IN;
            case ValEx literal -> literalKind(literal);
            default -> null;
        });
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
