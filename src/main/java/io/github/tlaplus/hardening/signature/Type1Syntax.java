package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.TlaType1;
import at.forsyte.apalache.tla.types.parser.DefaultType1Parser$;
import io.github.tlaplus.hardening.common.Diagnostics;
import java.util.Objects;

/**
 * Parses Apalache's type syntax, the syntax of the {@code type} fields in its IR JSON.
 *
 * <p>The parser is Apalache's Scala {@code DefaultType1Parser}, which the shaded facade contains
 * but the Java facade does not expose. This class is its only caller, so a facade upgrade that
 * drops it breaks type constraints here and nowhere else.
 */
final class Type1Syntax {
    private Type1Syntax() {}

    /**
     * Parses one type.
     *
     * @throws IllegalArgumentException if {@code text} is not a type
     */
    static TlaType1 parse(String text) {
        Objects.requireNonNull(text, "text");
        try {
            return DefaultType1Parser$.MODULE$.apply(text);
        } catch (Exception exception) {
            // Scala reports a syntax error as Type1ParseError, which is not a RuntimeException.
            throw new IllegalArgumentException(
                    "malformed type \"" + text + "\": " + Diagnostics.message(exception), exception);
        }
    }
}
