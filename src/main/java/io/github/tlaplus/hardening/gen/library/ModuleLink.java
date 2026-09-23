package io.github.tlaplus.hardening.gen.library;

import java.util.Objects;

/**
 * How one custom module is linked, and the module that its TLA+ source aliases instantiate:
 * the module itself unless the linkage names a separate TLC module.
 */
public record ModuleLink(LibraryLinkage linkage, String sourceModule) {
    public ModuleLink {
        Objects.requireNonNull(linkage, "linkage");
        OperatorId.requireIdentifier(sourceModule);
    }

    /** The link of {@code module} under a linkage that names no separate TLC module. */
    public static ModuleLink of(String module, LibraryLinkage linkage) {
        if (linkage.namesTlcModule()) {
            throw new IllegalArgumentException(linkage.encodedName() + " linkage of " + module
                    + " needs a TLC module");
        }
        return new ModuleLink(linkage, module);
    }

    /**
     * Checks that this link fits {@code module}: a separately named TLC module is present exactly
     * when the linkage names one, and then differs from the module.
     */
    public ModuleLink requireFor(String module) {
        if (linkage.namesTlcModule() == sourceModule.equals(module)) {
            throw new IllegalArgumentException(linkage.namesTlcModule()
                    ? linkage.encodedName() + " linkage of " + module + " needs a TLC module other than " + module
                    : linkage.encodedName() + " linkage of " + module + " cannot instantiate " + sourceModule);
        }
        return this;
    }
}
