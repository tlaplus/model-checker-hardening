package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.common.ExprEdge;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;

/**
 * Counts the temporal shapes of a module's properties (ADR 0028 §2).
 *
 * <p>A temporal operator is one of {@link #OPERATORS}. An occurrence reads the state when its
 * subexpression mentions a state variable, directly or through the definitions it applies. A shape
 * is an {@link ExprEdge} from the nearest enclosing temporal operator that reads the state, or
 * {@link #PROPERTY} at the top, to an occurrence that reads the state. Constant temporal formulas
 * therefore have no shapes.
 *
 * <p>The walk follows definitions and treats labels as {@link IrTree} does. A definition reached
 * under different enclosing operators is walked once per operator, since its shapes differ.
 */
public final class TemporalShapes {
    /** The parent of a shape that no temporal operator encloses. */
    public static final String PROPERTY = "PROPERTY";

    /** The temporal operators, named as in Apalache's IR JSON. */
    public static final Set<String> OPERATORS = Set.of(
            "GLOBALLY", "EVENTUALLY", "LEADS_TO", "WEAK_FAIRNESS", "STRONG_FAIRNESS",
            "STUTTER", "NO_STUTTER", "ENABLED");

    private TemporalShapes() {}

    /**
     * Returns how often each shape occurs in the definitions {@code roots} that the module defines.
     * A root the module does not define contributes nothing.
     */
    public static SortedMap<ExprEdge, Long> of(TlaModule module, List<String> roots) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(roots, "roots");
        var definitions = new HashMap<String, TlaOperDecl>();
        var variables = new HashSet<String>();
        for (var declaration : TlaModules.declarations(module)) {
            switch (declaration) {
                case TlaOperDecl definition -> definitions.put(definition.name(), definition);
                case TlaVarDecl variable -> variables.add(variable.name());
                default -> {
                    // Constants and assumptions are not definitions.
                }
            }
        }
        var walk = new Walk(variables);
        var scope = new Scope(definitions, null);
        for (var root : roots) {
            var definition = definitions.get(root);
            if (definition != null) {
                walk.definition(definition, scope, PROPERTY);
            }
        }
        return walk.shapes;
    }

    /** The definitions visible at one point: a {@code LET}'s, then those enclosing it. */
    private record Scope(Map<String, TlaOperDecl> definitions, Scope enclosing) {
        Scope resolve(String name) {
            for (var candidate = this; candidate != null; candidate = candidate.enclosing()) {
                if (candidate.definitions().containsKey(name)) {
                    return candidate;
                }
            }
            return null;
        }

        Scope with(LetInEx letIn) {
            var local = new HashMap<String, TlaOperDecl>();
            TlaExpressions.localDeclarations(letIn).forEach(declaration -> local.put(declaration.name(), declaration));
            return new Scope(local, this);
        }
    }

    private static final class Walk {
        private final Set<String> variables;
        private final SortedMap<ExprEdge, Long> shapes = new TreeMap<>();
        private final Map<TlaOperDecl, Boolean> reads = new IdentityHashMap<>();
        private final Map<TlaOperDecl, Set<String>> walked = new IdentityHashMap<>();

        Walk(Set<String> variables) {
            this.variables = variables;
        }

        void definition(TlaOperDecl definition, Scope scope, String enclosing) {
            if (walked.computeIfAbsent(definition, ignored -> new HashSet<>()).add(enclosing)) {
                expression(definition.body(), scope, enclosing);
            }
        }

        void expression(TlaEx expression, Scope scope, String enclosing) {
            switch (IrTree.unlabeled(expression)) {
                case NameEx name -> {
                    var defining = scope.resolve(name.name());
                    if (defining != null) {
                        definition(defining.definitions().get(name.name()), defining, enclosing);
                    }
                }
                case OperEx application -> {
                    var inner = enclosing;
                    var operator = application.oper().name();
                    if (OPERATORS.contains(operator) && readsState(application, scope)) {
                        shapes.merge(new ExprEdge(enclosing, operator), 1L, Long::sum);
                        inner = operator;
                    }
                    for (var argument : TlaExpressions.arguments(application)) {
                        expression(argument, scope, inner);
                    }
                }
                case LetInEx letIn -> expression(letIn.body(), scope.with(letIn), enclosing);
                default -> {
                    // A literal has no subexpressions.
                }
            }
        }

        /** Whether an expression mentions a state variable, directly or through a definition. */
        boolean readsState(TlaEx expression, Scope scope) {
            return switch (IrTree.unlabeled(expression)) {
                case NameEx name -> {
                    if (variables.contains(name.name())) {
                        yield true;
                    }
                    var defining = scope.resolve(name.name());
                    yield defining != null && readsState(defining.definitions().get(name.name()), defining);
                }
                case OperEx application -> TlaExpressions.arguments(application).stream()
                        .anyMatch(argument -> readsState(argument, scope));
                case LetInEx letIn -> readsState(letIn.body(), scope.with(letIn));
                default -> false;
            };
        }

        private boolean readsState(TlaOperDecl definition, Scope scope) {
            var known = reads.get(definition);
            if (known != null) {
                return known;
            }
            // A recursive definition reads the state only through its other subexpressions.
            reads.put(definition, false);
            var result = readsState(definition.body(), scope);
            reads.put(definition, result);
            return result;
        }
    }
}
