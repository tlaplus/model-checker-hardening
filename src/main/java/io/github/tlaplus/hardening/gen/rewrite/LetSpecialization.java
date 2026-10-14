package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaType1;
import io.github.tlaplus.hardening.gen.ir.IrTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;
import org.apalache_mc.tla.jir.TlaTypeUnifier;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Specializes every LET definition of a rule's replacement to the types at which the replacement
 * applies it. Snowcat generalizes a definition whose body reads no parameter of the rule, as
 * {@code F(v) == v} in {@code LET F(v) == v IN F(x)}, and types it over variables of its own. The
 * rewriter would take those for open variables of the match and draw them at random, so the
 * definition would be typed apart from its application, and later rewrites inside its body would
 * follow the wrong types.
 */
final class LetSpecialization {
    private LetSpecialization() {}

    /**
     * Returns {@code replacement} with each LET definition typed as it is applied, or rejects a
     * definition applied at types that would constrain the rule's own type variables.
     *
     * @param signature the type variables of the rule's parameters and pattern, which no definition
     *     may constrain
     */
    static TlaEx specialize(String rule, TlaEx replacement, Set<Integer> signature) {
        return switch (replacement) {
            case LetInEx let -> {
                var specialized = (LetInEx) IrTypes.substitute(let, uses(rule, let, signature));
                var declarations = new ArrayList<TlaOperDecl>();
                for (var declaration : TlaExpressions.localDeclarations(specialized)) {
                    declarations.add(TlaDeclarations.withBody(declaration,
                            specialize(rule, declaration.body(), signature)));
                }
                var body = specialize(rule, specialized.body(), signature);
                yield (TlaEx) TlaExpressions.letIn(body, declarations).withTag(specialized.typeTag());
            }
            case OperEx application -> {
                var arguments = TlaExpressions.arguments(application).stream()
                        .map(argument -> specialize(rule, argument, signature))
                        .toList();
                yield TlaExpressions.withArguments(application, arguments);
            }
            default -> replacement;
        };
    }

    /** Unifies the type of each definition of {@code let} with the type of every name that applies it. */
    private static TlaTypeSubstitution uses(String rule, LetInEx let, Set<Integer> signature) {
        var declarations = TlaExpressions.localDeclarations(let);
        var reserved = new ArrayList<TlaType1>();
        reserved.add(TlaTypes.typeOf(let));
        declarations.forEach(declaration -> reserved.add(TlaTypes.typeOf(declaration)));
        var unifier = new TlaTypeUnifier(reserved.toArray(TlaType1[]::new));
        var types = Optional.<TlaTypeSubstitution>empty();
        for (var index = 0; index < declarations.size(); index++) {
            var declaration = declarations.get(index);
            // A definition is visible in the definitions after it and in the body.
            var scope = new ArrayList<TlaEx>();
            declarations.subList(index + 1, declarations.size()).forEach(later -> scope.add(later.body()));
            scope.add(let.body());
            for (var use : applications(declaration.name(), scope)) {
                var unified = unifier.unify(types, TlaTypes.typeOf(declaration), use);
                if (unified.isEmpty()) {
                    throw RuleContract.rejected(rule, "the replacement applies " + declaration.name()
                            + " at types that do not unify");
                }
                types = Optional.of(unified.get().substitution());
            }
        }
        var substitution = types.orElse(TlaTypeSubstitution.empty());
        for (var variable : signature) {
            var type = TlaTypes.typeVariable(variable);
            if (!substitution.applyFully(type).equals(type)) {
                throw RuleContract.rejected(rule, "a LET definition of the replacement is applied at types that "
                        + "constrain the rule's parameters; apply each definition at one type");
            }
        }
        return substitution;
    }

    /** Returns the type of every occurrence of {@code name} in {@code scope}. */
    private static List<TlaType1> applications(String name, List<TlaEx> scope) {
        var types = new ArrayList<TlaType1>();
        for (var expression : scope) {
            TlaExpressions.forEach(expression, node -> {
                if (node instanceof NameEx occurrence && occurrence.name().equals(name)) {
                    types.add(TlaTypes.typeOf(occurrence));
                }
            });
        }
        return types;
    }
}
