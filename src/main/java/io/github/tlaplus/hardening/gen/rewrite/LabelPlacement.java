package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.ValEx;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;

/**
 * Where a rule's replacement may not carry a label of the node it rewrites. SANY requires a label
 * to declare exactly the binders between it and its definition, and rejects any label inside an
 * {@code EXCEPT} replacement. A label in a parameter's binding was valid where the pattern found
 * it, so it stays valid unless the replacement puts the parameter under a binder of its own, or
 * into an {@code EXCEPT} replacement.
 */
final class LabelPlacement {
    private LabelPlacement() {}

    /** Returns the parameters {@code replacement} places where a label of their binding is invalid. */
    static Set<String> unlabelled(TlaEx replacement, Set<String> parameters) {
        var unlabelled = new LinkedHashSet<String>();
        collect(replacement, false, parameters, unlabelled);
        return Set.copyOf(unlabelled);
    }

    private static void collect(TlaEx expression, boolean invalid, Set<String> parameters, Set<String> unlabelled) {
        switch (expression) {
            case NameEx name -> {
                if (invalid && parameters.contains(name.name())) {
                    unlabelled.add(name.name());
                }
            }
            case ValEx ignored -> {}
            case LetInEx let -> {
                TlaExpressions.localDeclarations(let)
                        .forEach(declaration -> collect(declaration.body(), invalid, parameters, unlabelled));
                collect(let.body(), invalid, parameters, unlabelled);
            }
            case OperEx application -> {
                var binding = IrBinding.of(application.oper());
                var binds = !IrBinding.boundNames(application).isEmpty();
                var arguments = TlaExpressions.arguments(application);
                for (var index = 0; index < arguments.size(); index++) {
                    if (binding.introduces(index)) {
                        continue;
                    }
                    var inner = invalid || binds && binding.scopes(index, arguments.size())
                            || RewritePositions.isExceptReplacement(application, index);
                    collect(arguments.get(index), inner, parameters, unlabelled);
                }
            }
            default -> throw new IllegalArgumentException("unsupported expression: " + expression);
        }
    }
}
