package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.*;
import at.forsyte.apalache.tla.lir.values.TlaPredefSet;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;

/** Lexical dependency analysis and injective name rewriting of library IR. */
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
                var syntax = LibrarySyntax.of(operator.oper());
                categories.addAll(syntax.categories);
                var args = TlaExpressions.arguments(operator);
                var scope = new HashSet<>(bound);
                switch (syntax.binding) {
                    case NONE -> args.forEach(arg -> inspect(arg, bound, free, categories));
                    case SINGLE_BOUNDED, SINGLE_UNBOUNDED -> {
                        addBindings(args.getFirst(), scope, categories);
                        if (syntax.binding == LibrarySyntax.Binding.SINGLE_BOUNDED) {
                            inspect(args.get(1), bound, free, categories);
                        }
                        inspect(args.getLast(), scope, free, categories);
                    }
                    case MULTIPLE -> {
                        for (int i = 1; i < args.size(); i += 2) {
                            addBindings(args.get(i), scope, categories);
                            inspect(args.get(i + 1), bound, free, categories);
                        }
                        inspect(args.getFirst(), scope, free, categories);
                    }
                }
            }
            default -> throw new IllegalArgumentException("unsupported imported expression: " + expression);
        }
    }

    private static void addBindings(TlaEx expression, Set<String> scope, Set<ExpressionCategory> categories) {
        categories.addAll(LibraryTypes.categories(TlaTypes.typeOf(expression)));
        if (expression instanceof NameEx name) scope.add(name.name());
        else if (expression instanceof OperEx tuple && LibrarySyntax.of(tuple.oper()) == LibrarySyntax.TUPLE_VALUE) {
            categories.add(ExpressionCategory.TUPLE);
            TlaExpressions.arguments(tuple).forEach(arg -> addBindings(arg, scope, categories));
        } else throw new IllegalArgumentException("unsupported binding: " + expression);
    }

    /** Rewrites every spelling injectively, including binders, preserving shadowing. */
    static TlaOperDecl rename(TlaOperDecl declaration, UnaryOperator<String> names) {
        return header(TlaDeclarations.rewrite(declaration, node -> renamed(node, names)), names);
    }

    /** Renames one already-rebuilt node, rejecting any shape the importer does not support. */
    private static TlaEx renamed(TlaEx expression, UnaryOperator<String> names) {
        return switch (expression) {
            case NameEx name -> TlaExpressions.withName(name, names.apply(name.name()));
            case LetInEx let -> TlaExpressions.withLocalDeclarations(let,
                    TlaExpressions.localDeclarations(let).stream()
                            .map(declaration -> header(declaration, names)).toList());
            case ValEx ignored -> expression;
            case OperEx ignored -> expression;
            default -> throw new IllegalArgumentException("unsupported imported expression: " + expression);
        };
    }

    /** Renames a declaration's own spellings; the walk has already rewritten its body. */
    private static TlaOperDecl header(TlaOperDecl declaration, UnaryOperator<String> names) {
        var parameters = TlaDeclarations.parameters(declaration).stream()
                .map(parameter -> names.apply(parameter.name())).toList();
        return TlaDeclarations.withHeader(declaration, names.apply(declaration.name()), parameters);
    }
}
