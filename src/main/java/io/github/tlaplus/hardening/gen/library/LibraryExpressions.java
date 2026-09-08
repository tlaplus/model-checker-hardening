package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.*;
import at.forsyte.apalache.tla.lir.values.TlaPredefSet;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import static io.github.tlaplus.hardening.common.ScalaCollections.*;

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
        categories.addAll(LibraryTypes.categories(LibraryTypes.type(declaration.typeTag())));
        var scope = new HashSet<>(bound);
        list(declaration.formalParams()).forEach(parameter -> scope.add(parameter.name()));
        inspect(declaration.body(), scope, free, categories);
    }

    private static void inspect(TlaEx expression, Set<String> bound,
            Set<String> free, Set<ExpressionCategory> categories) {
        categories.addAll(LibraryTypes.categories(LibraryTypes.type(expression.typeTag())));
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
                list(let.decls()).forEach(declaration -> scope.add(declaration.name()));
                list(let.decls()).forEach(declaration -> inspectDeclaration(declaration, scope, free, categories));
                inspect(let.body(), scope, free, categories);
            }
            case OperEx operator -> {
                var syntax = LibrarySyntax.of(operator.oper());
                categories.addAll(syntax.categories);
                var args = list(operator.args());
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
        categories.addAll(LibraryTypes.categories(LibraryTypes.type(expression.typeTag())));
        if (expression instanceof NameEx name) scope.add(name.name());
        else if (expression instanceof OperEx tuple && LibrarySyntax.of(tuple.oper()) == LibrarySyntax.TUPLE_VALUE) {
            categories.add(ExpressionCategory.TUPLE);
            list(tuple.args()).forEach(arg -> addBindings(arg, scope, categories));
        } else throw new IllegalArgumentException("unsupported binding: " + expression);
    }

    /** Rewrites every spelling injectively, including binders, preserving shadowing. Always copies. */
    static TlaOperDecl rename(TlaOperDecl declaration, UnaryOperator<String> names) {
        var params = list(declaration.formalParams()).stream()
                .map(p -> new OperParam(names.apply(p.name()), p.arity())).toList();
        var result = new TlaOperDecl(names.apply(declaration.name()), seq(params).toList(),
                rename(declaration.body(), names), declaration.typeTag());
        result.isRecursive_$eq(declaration.isRecursive());
        return result;
    }

    private static TlaEx rename(TlaEx expression, UnaryOperator<String> names) {
        return switch (expression) {
            case NameEx name -> new NameEx(names.apply(name.name()), name.typeTag());
            case ValEx value -> new ValEx(value.value(), value.typeTag());
            case OperEx operator -> new OperEx(operator.oper(), seq(list(operator.args()).stream()
                    .map(arg -> rename(arg, names)).toList()), operator.typeTag());
            case LetInEx let -> new LetInEx(rename(let.body(), names), seq(list(let.decls()).stream()
                    .map(decl -> rename(decl, names)).toList()), let.typeTag());
            default -> throw new IllegalArgumentException("unsupported imported expression: " + expression);
        };
    }

    /** Collects name references; library names are in a namespace no generated binder uses. */
    static void references(TlaEx expression, Set<String> names) {
        switch (expression) {
            case NameEx name -> names.add(name.name());
            case OperEx operator -> list(operator.args()).forEach(arg -> references(arg, names));
            case LetInEx let -> {
                references(let.body(), names);
                list(let.decls()).forEach(decl -> references(decl.body(), names));
            }
            default -> { }
        }
    }
}
