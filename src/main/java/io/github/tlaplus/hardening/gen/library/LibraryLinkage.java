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
    INLINE("inline", false, false, false),
    /**
     * The TLA+ source calls the module through a named {@code INSTANCE} resolved on the checker
     * class path, while Apalache evaluates the definitions it imports for {@code EXTENDS Module}.
     */
    INSTANCE("instance", true, true, false),
    /**
     * The TLA+ source calls a different module, which defines the same operators, through a named
     * {@code INSTANCE}, while Apalache evaluates the module's own definitions (ADR 0015). Only
     * the latter pass library validation, so the former may be recursive.
     */
    DIFF("diff", true, false, true);

    private final String encodedName;
    private final boolean aliasesSource;
    private final boolean typechecksThroughWrapper;
    private final boolean namesTlcModule;

    LibraryLinkage(String encodedName, boolean aliasesSource, boolean typechecksThroughWrapper,
            boolean namesTlcModule) {
        this.encodedName = encodedName;
        this.aliasesSource = aliasesSource;
        this.typechecksThroughWrapper = typechecksThroughWrapper;
        this.namesTlcModule = namesTlcModule;
    }

    public String encodedName() {
        return encodedName;
    }

    /**
     * Whether the TLA+ source replaces used exports by aliases of a named instance, which the
     * parser and TLC resolve on the library classpath.
     */
    public boolean aliasesSource() {
        return aliasesSource;
    }

    /**
     * Whether Apalache typechecks a wrapper {@code EXTENDS Module} rather than the module itself,
     * so the library holds what Apalache imports, including its rewired Community Modules.
     */
    public boolean typechecksThroughWrapper() {
        return typechecksThroughWrapper;
    }

    /** Whether the TLA+ source instantiates a separately named module rather than the module. */
    public boolean namesTlcModule() {
        return namesTlcModule;
    }

    public static Optional<LibraryLinkage> fromEncodedName(String encodedName) {
        Objects.requireNonNull(encodedName, "encodedName");
        for (var linkage : values()) {
            if (linkage.encodedName.equals(encodedName)) return Optional.of(linkage);
        }
        return Optional.empty();
    }
}
