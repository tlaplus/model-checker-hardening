package io.github.tlaplus.hardening.gen.ir;

import static org.apalache_mc.tla.jir.TlaOperators.OPER_APP;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.ValEx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;

/**
 * Substitution and beta-reduction of typed IR.
 *
 * <p>Substitution does not rename binders. It refuses a replacement that a binder of the target
 * would capture instead, so a caller keeps the names it binds apart from the names it substitutes,
 * as the rewriter does by binding only fresh names. Every inserted replacement is a fresh copy,
 * because Apalache requires unique node identities.
 */
public final class IrSubstitution {
    private IrSubstitution() {}

    /**
     * Replaces every free occurrence of a name of {@code replacements} in {@code expression}.
     *
     * @throws IllegalArgumentException if a binder of {@code expression} would capture a free name
     *     of a replacement it applies to
     */
    public static TlaEx substitute(TlaEx expression, Map<String, ? extends TlaEx> replacements) {
        Objects.requireNonNull(expression, "expression");
        var copy = Map.<String, TlaEx>copyOf(Objects.requireNonNull(replacements, "replacements"));
        return copy.isEmpty() ? expression : substitute(expression, copy, freeNames(copy));
    }

    /**
     * Applies every lambda: {@code (LAMBDA p1, ..., pn : body)(a1, ..., an)} becomes {@code body}
     * with each {@code ai} substituted for {@code pi}.
     */
    public static TlaEx betaReduce(TlaEx expression) {
        return TlaExpressions.rewrite(Objects.requireNonNull(expression, "expression"), node -> {
            if (!(node instanceof OperEx application) || application.oper() != OPER_APP) {
                return node;
            }
            var arguments = TlaExpressions.arguments(application);
            var lambda = lambda(arguments.getFirst());
            if (lambda.isEmpty()) {
                return node;
            }
            var parameters = TlaDeclarations.parameters(lambda.get());
            var bindings = new HashMap<String, TlaEx>();
            for (var index = 0; index < parameters.size(); index++) {
                bindings.put(parameters.get(index).name(), arguments.get(index + 1));
            }
            // An argument may itself be a lambda the body applies, so reduce the result again.
            return betaReduce(substitute(lambda.get().body(), bindings));
        });
    }

    /** Returns the definition of a lambda, which the IR spells {@code LET f(p) == body IN f}. */
    public static Optional<TlaOperDecl> lambda(TlaEx expression) {
        if (expression instanceof LetInEx let && let.body() instanceof NameEx name) {
            var declarations = TlaExpressions.localDeclarations(let);
            if (declarations.size() == 1 && declarations.getFirst().name().equals(name.name())) {
                return Optional.of(declarations.getFirst());
            }
        }
        return Optional.empty();
    }

    private static Map<String, Set<String>> freeNames(Map<String, TlaEx> replacements) {
        var result = new HashMap<String, Set<String>>();
        replacements.forEach((name, replacement) -> result.put(name, IrNames.free(replacement)));
        return result;
    }

    private static TlaEx substitute(TlaEx expression, Map<String, TlaEx> replacements, Map<String, Set<String>> free) {
        return switch (expression) {
            case NameEx name -> replacements.containsKey(name.name())
                    ? TlaExpressions.deepCopy(replacements.get(name.name()))
                    : expression;
            case ValEx ignored -> expression;
            case LetInEx let -> substituteLet(let, replacements, free);
            case OperEx application -> substituteApplication(application, replacements, free);
            default -> throw new IllegalArgumentException("unsupported expression: " + expression);
        };
    }

    private static TlaEx substituteApplication(
            OperEx application, Map<String, TlaEx> replacements, Map<String, Set<String>> free) {
        var binding = IrBinding.of(application.oper());
        var arguments = TlaExpressions.arguments(application);
        var inner = within(IrBinding.boundNames(application).stream().map(NameEx::name).toList(), replacements, free);
        var rebuilt = new ArrayList<TlaEx>(arguments.size());
        for (var index = 0; index < arguments.size(); index++) {
            var argument = arguments.get(index);
            rebuilt.add(binding.introduces(index) ? argument
                    : substitute(argument, binding.scopes(index, arguments.size()) ? inner : replacements, free));
        }
        return TlaExpressions.withArguments(application, rebuilt);
    }

    private static TlaEx substituteLet(LetInEx let, Map<String, TlaEx> replacements, Map<String, Set<String>> free) {
        var declarations = TlaExpressions.localDeclarations(let);
        var inner = within(declarations.stream().map(TlaOperDecl::name).toList(), replacements, free);
        var rebuilt = new ArrayList<TlaOperDecl>(declarations.size());
        for (var declaration : declarations) {
            var parameters = TlaDeclarations.parameters(declaration).stream().map(parameter -> parameter.name()).toList();
            rebuilt.add(TlaDeclarations.withBody(declaration,
                    substitute(declaration.body(), within(parameters, inner, free), free)));
        }
        return TlaExpressions.letIn(substitute(let.body(), inner, free), rebuilt);
    }

    /**
     * Returns the replacements that apply inside a scope binding {@code bound}: a bound name shadows
     * its own replacement, and must not capture a free name of another.
     */
    private static Map<String, TlaEx> within(
            List<String> bound, Map<String, TlaEx> replacements, Map<String, Set<String>> free) {
        if (bound.isEmpty()) {
            return replacements;
        }
        var inner = new HashMap<>(replacements);
        bound.forEach(inner::remove);
        var shadowed = new HashSet<>(bound);
        for (var name : inner.keySet()) {
            for (var captured : free.get(name)) {
                if (shadowed.contains(captured)) {
                    throw new IllegalArgumentException(
                            "binder " + captured + " would capture the replacement of " + name);
                }
            }
        }
        return inner;
    }
}
