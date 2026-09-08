package io.github.tlaplus.hardening.common;

import static io.github.tlaplus.hardening.common.ScalaCollections.list;
import static io.github.tlaplus.hardening.common.ScalaCollections.seq;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The one structural walk over the IR expression shapes this project constructs and imports.
 *
 * <p>{@link #rewrite} is bottom-up and always allocates a fresh {@link OperEx}, {@link LetInEx}
 * and {@link TlaOperDecl}, so a result never shares a mutable declaration with its input. Nodes it
 * does not descend into reach the hook unchanged; a caller needing a closed vocabulary rejects
 * them there.
 */
public final class TlaExpressions {
    private TlaExpressions() {}

    /** Rebuilds the expression bottom-up, offering every rebuilt node to {@code replace}. */
    public static TlaEx rewrite(TlaEx expression, UnaryOperator<TlaEx> replace) {
        return replace.apply(switch (expression) {
            case OperEx operator -> new OperEx(
                    operator.oper(),
                    seq(list(operator.args()).stream()
                            .map(argument -> rewrite(argument, replace))
                            .toList()),
                    operator.typeTag());
            case LetInEx let -> new LetInEx(
                    rewrite(let.body(), replace),
                    seq(list(let.decls()).stream()
                            .map(declaration -> rewrite(declaration, replace))
                            .toList()),
                    let.typeTag());
            default -> expression;
        });
    }

    /** Rebuilds one declaration, preserving its name, parameters and recursion flag. */
    public static TlaOperDecl rewrite(TlaOperDecl declaration, UnaryOperator<TlaEx> replace) {
        var rebuilt = new TlaOperDecl(
                declaration.name(),
                declaration.formalParams(),
                rewrite(declaration.body(), replace),
                declaration.typeTag());
        rebuilt.isRecursive_$eq(declaration.isRecursive());
        return rebuilt;
    }

    /** Returns a fresh declaration that shares no mutable node with {@code declaration}. */
    public static TlaOperDecl copy(TlaOperDecl declaration) {
        return rewrite(declaration, UnaryOperator.identity());
    }

    /** Visits the expression and every subexpression, outermost first. */
    public static void forEach(TlaEx expression, Consumer<TlaEx> visit) {
        visit.accept(expression);
        switch (expression) {
            case OperEx operator ->
                list(operator.args()).forEach(argument -> forEach(argument, visit));
            case LetInEx let -> {
                forEach(let.body(), visit);
                list(let.decls()).forEach(declaration -> forEach(declaration.body(), visit));
            }
            default -> { }
        }
    }
}
