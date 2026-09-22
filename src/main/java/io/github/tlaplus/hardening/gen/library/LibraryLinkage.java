package io.github.tlaplus.hardening.gen.library;

import java.util.Objects;
import java.util.Optional;

/**
 * How a custom module's definitions reach the TLA+ source of the parser and TLC. Apalache's JSON
 * input always inlines them. The encoded names appear in configuration and in the replay
 * manifest.
 */
public enum LibraryLinkage {
    /** Every checker evaluates the same inlined definitions. */
    INLINE("inline"),
    /**
     * The TLA+ source calls the module through a named {@code INSTANCE} resolved on the checker
     * class path, while Apalache evaluates the definitions it imports for {@code EXTENDS Module}.
     */
    INSTANCE("instance");

    private final String encodedName;

    LibraryLinkage(String encodedName) {
        this.encodedName = encodedName;
    }

    public String encodedName() {
        return encodedName;
    }

    public static Optional<LibraryLinkage> fromEncodedName(String encodedName) {
        Objects.requireNonNull(encodedName, "encodedName");
        for (var linkage : values()) {
            if (linkage.encodedName.equals(encodedName)) return Optional.of(linkage);
        }
        return Optional.empty();
    }
}
