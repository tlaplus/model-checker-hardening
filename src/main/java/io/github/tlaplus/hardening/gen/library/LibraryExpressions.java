package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.*;
import at.forsyte.apalache.tla.lir.values.TlaPredefSet;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;

/** Lexical dependency analysis of library IR. */
final class LibraryExpressions {
    record Facts(Set<String> freeNames, Set<ExpressionCategory> categories) {}

    private LibraryExpressions() {}

    static Facts inspect(TlaOperDecl declaration) {
        var names = new LinkedHashSet<String>();
        var categories = EnumSet.noneOf(ExpressionCategory.class);
        inspectDeclaration(declaration, Set.of(), names, categories);
        return new Facts(Set.copyOf(names), Set.copyOf(categories));
    }

    private static void inspectDeclaration(TlaOperDecl declaration, Set<String> bound,
            Set<String> free, Set<ExpressionCategory> categories) {
        if (declaration.isRecursive()) {
            throw new IllegalArgumentException("recursive custom operator: " + declaration.name());
        }
        categories.addAll(LibraryTypes.categories(TlaTypes.typeOf(declaration)));
        var scope = new HashSet<>(bound);
        TlaDeclarations.parameters(declaration).forEach(parameter -> scope.add(parameter.name()));
        inspect(declaration.body(), scope, free, categories);
    }

    private static void inspect(TlaEx expression, Set<String> bound,
            Set<String> free, Set<ExpressionCategory> categories) {
        categories.addAll(LibraryTypes.categories(TlaTypes.typeOf(expression)));
        switch (expression) {
            case NameEx name -> {
                if (!bound.contains(name.name())) free.add(name.name());
            }
            case ValEx value -> {
                if (value.value() instanceof TlaPredefSet) categories.add(ExpressionCategory.UNIVERSE);
            }
            case LetInEx let -> {
                categories.add(ExpressionCategory.OPERATOR);
                var scope = new HashSet<>(bound);
                var declarations = TlaExpressions.localDeclarations(let);
                declarations.forEach(declaration -> scope.add(declaration.name()));
                declarations.forEach(declaration -> inspectDeclaration(declaration, scope, free, categories));
                inspect(let.body(), scope, free, categories);
            }
            case OperEx operator -> {
                categories.addAll(LibrarySyntax.of(operator.oper()).categories);
                var binding = IrBinding.of(operator.oper());
                var args = TlaExpressions.arguments(operator);
                var scope = new HashSet<>(bound);
                for (var index = 0; index < args.size(); index++) {
                    if (binding.introduces(index)) addBindings(args.get(index), scope, categories);
                }
                for (var index = 0; index < args.size(); index++) {
                    if (!binding.introduces(index)) {
                        inspect(args.get(index), binding.scopes(index, args.size()) ? scope : bound, free, categories);
                    }
                }
            }
            default -> throw new IllegalArgumentException("unsupported imported expression: " + expression);
        }
    }

    private static void addBindings(TlaEx expression, Set<String> scope, Set<ExpressionCategory> categories) {
        categories.addAll(LibraryTypes.categories(TlaTypes.typeOf(expression)));
        if (expression instanceof OperEx) categories.add(ExpressionCategory.TUPLE);
        IrBinding.names(expression).forEach(name -> scope.add(name.name()));
    }
}
