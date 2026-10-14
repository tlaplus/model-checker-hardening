package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.ValEx;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;

/**
 * Where a rule's replacement may not carry a label of the node it rewrites. SANY requires a label
 * to declare exactly the binders between it and its definition, and rejects any label inside an
 * {@code EXCEPT} replacement. A label in a parameter's binding was valid where the pattern found
 * it, so it stays valid unless the replacement puts the parameter:
 *
 * <ul>
 *   <li>under a binder of its own;
 *   <li>into an {@code EXCEPT} replacement;
 *   <li>into a {@code LET} definition or lambda body, which starts a new label scope;
 *   <li>into an argument of a higher-order parameter, which beta reduction moves under the binders
 *       of the parameter's body.
 * </ul>
 *
 * <p>A higher-order parameter applied to anything but variables that the replacement binds is
 * itself unlabelled: a label in its body declares the variable that beta reduction replaces.
 */
final class LabelPlacement {
    private LabelPlacement() {}

    /** Returns the parameters {@code replacement} places where a label of their binding is invalid. */
    static Set<String> unlabelled(TlaEx replacement, Set<String> parameters) {
        var unlabelled = new LinkedHashSet<String>();
        collect(replacement, false, Set.of(), parameters, unlabelled);
        return Set.copyOf(unlabelled);
    }

    /** {@code bound} holds the variables that binders of the replacement introduce around the expression. */
    private static void collect(
            TlaEx expression, boolean invalid, Set<String> bound, Set<String> parameters, Set<String> unlabelled) {
        switch (expression) {
            case NameEx name -> {
                if (invalid && parameters.contains(name.name())) {
                    unlabelled.add(name.name());
                }
            }
            case ValEx ignored -> {}
            case LetInEx let -> {
                TlaExpressions.localDeclarations(let)
                        .forEach(declaration -> collect(declaration.body(), true, bound, parameters, unlabelled));
                collect(let.body(), invalid, bound, parameters, unlabelled);
            }
            case OperEx application when appliesParameter(application, parameters) -> {
                var arguments = TlaExpressions.arguments(application);
                var substituted = arguments.subList(1, arguments.size()).stream()
                        .anyMatch(argument -> !(argument instanceof NameEx variable && bound.contains(variable.name())));
                collect(arguments.getFirst(), invalid || substituted, bound, parameters, unlabelled);
                arguments.subList(1, arguments.size())
                        .forEach(argument -> collect(argument, true, bound, parameters, unlabelled));
            }
            case OperEx application -> {
                var binding = IrBinding.of(application.oper());
                var introduced = IrBinding.boundNames(application);
                var inner = new HashSet<>(bound);
                introduced.forEach(variable -> inner.add(variable.name()));
                var arguments = TlaExpressions.arguments(application);
                for (var index = 0; index < arguments.size(); index++) {
                    if (binding.introduces(index)) {
                        continue;
                    }
                    var scoped = !introduced.isEmpty() && binding.scopes(index, arguments.size());
                    var inside = invalid || scoped || RewritePositions.isExceptReplacement(application, index);
                    collect(arguments.get(index), inside, scoped ? inner : bound, parameters, unlabelled);
                }
            }
            default -> throw new IllegalArgumentException("unsupported expression: " + expression);
        }
    }

    /** Whether {@code application} applies a parameter, which only a higher-order parameter can be. */
    private static boolean appliesParameter(OperEx application, Set<String> parameters) {
        return application.oper() == TlaOperators.OPER_APP
                && TlaExpressions.arguments(application).getFirst() instanceof NameEx head
                && parameters.contains(head.name());
    }
}
