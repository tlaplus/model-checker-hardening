package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaOperators;

/**
 * The subexpressions a signature is tried against: those the tools evaluate.
 *
 * <p>The walk starts at the given root definitions and follows every reference to another
 * top-level definition. A top-level definition nothing references is skipped: Apalache drops it
 * before rewriting, and on corpus12 a shape inside one almost never failed. Inside reached code,
 * every {@code LET} definition is walked, referenced or not, lambdas included: Apalache reaches
 * unreferenced ones often enough that skipping them lost most failures a signature targets (ADR
 * 0006 records the measurement). Each definition is walked once, so the first match of a
 * signature is the first one a reader of the roots meets.
 *
 * <p>Labels are transparent: a label is semantically void, the checkers ignore it, and
 * {@code print --apalache-ir} omits it, so a pattern sees the labeled expression in its place and
 * never the label's name or parameters.
 */
final class IrTree {
    private IrTree() {}

    /**
     * Returns the subexpressions reachable from the root definitions, in pre-order.
     *
     * @throws IllegalArgumentException if the module does not define a root
     */
    static List<TlaEx> evaluatedSubexpressions(TlaModule module, List<String> roots) {
        Objects.requireNonNull(module, "module");
        var definitions = new HashMap<String, TlaOperDecl>();
        for (var declaration : TlaModules.declarations(module)) {
            if (declaration instanceof TlaOperDecl definition) {
                definitions.put(definition.name(), definition);
            }
        }
        var walk = new Walk();
        var scope = new Scope(definitions, null);
        for (var root : roots) {
            var definition = definitions.get(root);
            if (definition == null) {
                throw new IllegalArgumentException("the module does not define " + root);
            }
            walk.definition(definition, scope);
        }
        return walk.result;
    }

    /** Removes the labels wrapped around an expression. */
    static TlaEx unlabeled(TlaEx expression) {
        var result = Objects.requireNonNull(expression, "expression");
        while (isLabel(result)) {
            result = TlaExpressions.arguments((OperEx) result).getFirst();
        }
        return result;
    }

    private static boolean isLabel(TlaEx expression) {
        return expression instanceof OperEx application && application.oper() == TlaOperators.LABEL;
    }

    /** The definitions visible at one point: a {@code LET}'s, then those enclosing it. */
    private record Scope(Map<String, TlaOperDecl> definitions, Scope enclosing) {}

    private static final class Walk {
        private final List<TlaEx> result = new ArrayList<>();
        private final Set<TlaOperDecl> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        /** Walks a definition's body once, resolving its references where it is defined. */
        void definition(TlaOperDecl definition, Scope scope) {
            if (visited.add(definition)) {
                expression(definition.body(), scope);
            }
        }

        void expression(TlaEx expression, Scope scope) {
            var node = unlabeled(expression);
            result.add(node);
            switch (node) {
                case NameEx name -> reference(name.name(), scope);
                case OperEx application -> {
                    for (var argument : TlaExpressions.arguments(application)) {
                        expression(argument, scope);
                    }
                }
                case LetInEx letIn -> {
                    var local = new HashMap<String, TlaOperDecl>();
                    var declarations = TlaExpressions.localDeclarations(letIn);
                    declarations.forEach(declaration -> local.put(declaration.name(), declaration));
                    var letScope = new Scope(local, scope);
                    declarations.forEach(declaration -> definition(declaration, letScope));
                    expression(letIn.body(), letScope);
                }
                default -> {
                    // A literal has no subexpressions.
                }
            }
        }

        private void reference(String name, Scope scope) {
            for (var candidate = scope; candidate != null; candidate = candidate.enclosing()) {
                var definition = candidate.definitions().get(name);
                if (definition != null) {
                    definition(definition, candidate);
                    return;
                }
            }
        }
    }
}
