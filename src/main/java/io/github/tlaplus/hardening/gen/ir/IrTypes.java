package io.github.tlaplus.hardening.gen.ir;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.Typed;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;
import org.apalache_mc.tla.jir.TlaTypes;

/** Type tags of typed IR. */
public final class IrTypes {
    private IrTypes() {}

    /**
     * Applies {@code substitution} to the type tag of every node and local definition, which
     * instantiates an expression Snowcat typed with type variables.
     */
    public static TlaEx substitute(TlaEx expression, TlaTypeSubstitution substitution) {
        Objects.requireNonNull(substitution, "substitution");
        return TlaExpressions.rewrite(Objects.requireNonNull(expression, "expression"), node -> {
            var retyped = node instanceof LetInEx let
                    ? TlaExpressions.withLocalDeclarations(let, TlaExpressions.localDeclarations(let).stream()
                            .map(declaration -> retag(declaration, substitution)).toList())
                    : node;
            return (TlaEx) retyped.withTag(Typed.apply(substitution.applyFully(TlaTypes.typeOf(node))));
        });
    }

    private static TlaOperDecl retag(TlaOperDecl declaration, TlaTypeSubstitution substitution) {
        return declaration.withTag(Typed.apply(substitution.applyFully(TlaTypes.typeOf(declaration))));
    }
}
