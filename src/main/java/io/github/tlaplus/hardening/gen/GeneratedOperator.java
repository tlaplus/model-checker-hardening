package io.github.tlaplus.hardening.gen;

import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.util.Objects;

/** A generated definition, stored once in module declaration order. */
public sealed interface GeneratedOperator permits GeneratedOperator.Auxiliary, GeneratedActionOperator {
    TlaOperDecl declaration();

    /** A state-free definition, closed over its parameters and earlier auxiliary definitions. */
    record Auxiliary(TlaOperDecl declaration) implements GeneratedOperator {
        public Auxiliary {
            Objects.requireNonNull(declaration, "declaration");
        }
    }
}
