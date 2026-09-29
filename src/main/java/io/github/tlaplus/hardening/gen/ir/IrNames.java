package io.github.tlaplus.hardening.gen.ir;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.ValEx;
import at.forsyte.apalache.tla.lir.values.TlaStr;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;

/** Names in typed IR: injective renaming and the names an expression reads from its context. */
public final class IrNames {
    private IrNames() {}

    /** Rewrites every spelling of a declaration injectively, including binders, preserving shadowing. */
    public static TlaOperDecl rename(TlaOperDecl declaration, UnaryOperator<String> names) {
        return header(TlaDeclarations.rewrite(declaration, node -> renamed(node, names)), names);
    }

    /** Rewrites every spelling of an expression injectively, including binders, preserving shadowing. */
    public static TlaEx rename(TlaEx expression, UnaryOperator<String> names) {
        return TlaExpressions.rewrite(expression, node -> renamed(node, names));
    }

    /**
     * Returns the names {@code expression} reads without binding them: state variables, operators,
     * parameters and the bound names of an enclosing context.
     */
    public static Set<String> free(TlaEx expression) {
        var free = new LinkedHashSet<String>();
        collectFree(expression, Set.of(), free);
        return Set.copyOf(free);
    }

    /** Returns every name {@code expression} binds: quantified variables, LET definitions and their parameters. */
    public static Set<String> bound(TlaEx expression) {
        var bound = new LinkedHashSet<String>();
        TlaExpressions.forEach(expression, node -> {
            if (node instanceof OperEx application) {
                IrBinding.boundNames(application).forEach(name -> bound.add(name.name()));
            } else if (node instanceof LetInEx let) {
                for (var declaration : TlaExpressions.localDeclarations(let)) {
                    bound.add(declaration.name());
                    TlaDeclarations.parameters(declaration).forEach(parameter -> bound.add(parameter.name()));
                }
            }
        });
        return Set.copyOf(bound);
    }

    /** Returns the name of every label in {@code expression}. */
    public static Set<String> labels(TlaEx expression) {
        var labels = new LinkedHashSet<String>();
        TlaExpressions.forEach(expression, node -> labelName(node).ifPresent(labels::add));
        return Set.copyOf(labels);
    }

    /**
     * Renames every label that repeats the name of a label visited before it, bottom-up, to a name
     * from {@code fresh}, so that no two labels of the result share a name. SANY rejects a
     * definition with two labels of one name, which a copied subexpression would otherwise have.
     */
    public static TlaEx relabelRepeats(TlaEx expression, Supplier<String> fresh) {
        Objects.requireNonNull(fresh, "fresh");
        var seen = new HashSet<String>();
        return TlaExpressions.rewrite(expression, node -> {
            var name = labelName(node);
            if (name.isEmpty() || seen.add(name.get())) {
                return node;
            }
            var arguments = new ArrayList<>(TlaExpressions.arguments((OperEx) node));
            var renamed = fresh.get();
            seen.add(renamed);
            arguments.set(1, new TlaTypedScopeUncheckedBuilder().str(renamed));
            return TlaExpressions.withArguments((OperEx) node, arguments);
        });
    }

    /** A label is {@code LABEL(body, name, parameters...)}, each name a string literal. */
    private static Optional<String> labelName(TlaEx node) {
        return node instanceof OperEx application && application.oper() == TlaOperators.LABEL
                && TlaExpressions.arguments(application).get(1) instanceof ValEx value
                && value.value() instanceof TlaStr name
                ? Optional.of(name.value()) : Optional.empty();
    }

    private static void collectFree(TlaEx expression, Set<String> bound, Set<String> free) {
        switch (expression) {
            case NameEx name -> {
                if (!bound.contains(name.name())) free.add(name.name());
            }
            case ValEx ignored -> {}
            case LetInEx let -> {
                var scope = new HashSet<>(bound);
                TlaExpressions.localDeclarations(let).forEach(declaration -> scope.add(declaration.name()));
                for (var declaration : TlaExpressions.localDeclarations(let)) {
                    var inner = new HashSet<>(scope);
                    TlaDeclarations.parameters(declaration).forEach(parameter -> inner.add(parameter.name()));
                    collectFree(declaration.body(), inner, free);
                }
                collectFree(let.body(), scope, free);
            }
            case OperEx application -> {
                var binding = IrBinding.of(application.oper());
                var arguments = TlaExpressions.arguments(application);
                var scope = new HashSet<>(bound);
                IrBinding.boundNames(application).forEach(name -> scope.add(name.name()));
                for (var index = 0; index < arguments.size(); index++) {
                    if (!binding.introduces(index)) {
                        collectFree(arguments.get(index), binding.scopes(index, arguments.size()) ? scope : bound, free);
                    }
                }
            }
            default -> throw new IllegalArgumentException("unsupported expression: " + expression);
        }
    }

    /** Renames one already-rebuilt node, rejecting any shape the IR walks do not support. */
    private static TlaEx renamed(TlaEx expression, UnaryOperator<String> names) {
        return switch (expression) {
            case NameEx name -> TlaExpressions.withName(name, names.apply(name.name()));
            case LetInEx let -> TlaExpressions.withLocalDeclarations(let,
                    TlaExpressions.localDeclarations(let).stream()
                            .map(declaration -> header(declaration, names)).toList());
            case ValEx ignored -> expression;
            case OperEx ignored -> expression;
            default -> throw new IllegalArgumentException("unsupported expression: " + expression);
        };
    }

    /** Renames a declaration's own spellings; the walk has already rewritten its body. */
    private static TlaOperDecl header(TlaOperDecl declaration, UnaryOperator<String> names) {
        var parameters = TlaDeclarations.parameters(declaration).stream()
                .map(parameter -> names.apply(parameter.name())).toList();
        return TlaDeclarations.withHeader(declaration, names.apply(declaration.name()), parameters);
    }
}
