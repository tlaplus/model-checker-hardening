package io.github.tlaplus.hardening.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The top-level packages of FuzzTLA and the modules each may depend on directly (ADR 0022).
 *
 * <p>This enum is the authoritative layering, and {@code LayeringTest} checks the bytecode
 * against it. A constant may list only constants declared before it, so the compiler rejects a
 * declared cycle as a forward reference. The allowed sets are direct dependencies, not their
 * transitive closure: a new edge between modules is a change of one constant.
 */
@ArchRole(Role.MODEL)
@Key
public enum ArchModule {
    COMMON("common"),
    CHECKER("checker", COMMON),
    GEN("gen", COMMON),
    SIGNATURE("signature", COMMON),
    MUTATION("mutation", COMMON),
    CORPUS("corpus", COMMON, CHECKER, GEN, MUTATION),
    CONFIG("config", COMMON, CORPUS, GEN, MUTATION),
    DATABASE("database", COMMON, CHECKER, CORPUS, GEN, MUTATION),
    WORKFLOW("workflow", COMMON, CHECKER, CONFIG, CORPUS, GEN, MUTATION, SIGNATURE),
    CLI("cli", COMMON, CHECKER, GEN, SIGNATURE, MUTATION, CORPUS, CONFIG, DATABASE, WORKFLOW),
    /** The root package, which holds the launcher; it has no subpackages. */
    LAUNCHER("", CLI);

    /** The root package of FuzzTLA; every module is this package or one of its subpackages. */
    public static final String ROOT_PACKAGE = "io.github.tlaplus.hardening";

    private final String segment;
    private final List<ArchModule> allowed;

    ArchModule(String segment, ArchModule... allowed) {
        this.segment = segment;
        this.allowed = List.of(allowed);
    }

    /** The module's package, such as {@code io.github.tlaplus.hardening.corpus}. */
    public String packageName() {
        return segment.isEmpty() ? ROOT_PACKAGE : ROOT_PACKAGE + "." + segment;
    }

    /** The module's name in the atlas: its package below {@link #ROOT_PACKAGE}, or {@code root}. */
    public String displayName() {
        return segment.isEmpty() ? "root" : segment;
    }

    /** The modules this one may depend on directly, in declaration order. */
    public List<ArchModule> allowed() {
        return allowed;
    }

    /** Whether code in this module may refer to code in {@code target}. */
    public boolean allows(ArchModule target) {
        return target == this || allowed.contains(target);
    }

    /**
     * The module of a package: the constant named by its first segment below {@link
     * #ROOT_PACKAGE}, or {@link #LAUNCHER} for the root package itself. Empty for a package
     * outside FuzzTLA or a top-level package that no constant declares.
     */
    public static Optional<ArchModule> ofPackage(String packageName) {
        if (packageName.equals(ROOT_PACKAGE)) {
            return Optional.of(LAUNCHER);
        }
        if (!packageName.startsWith(ROOT_PACKAGE + ".")) {
            return Optional.empty();
        }
        var rest = packageName.substring(ROOT_PACKAGE.length() + 1);
        var dot = rest.indexOf('.');
        var segment = dot < 0 ? rest : rest.substring(0, dot);
        return Arrays.stream(values()).filter(m -> m.segment.equals(segment)).findFirst();
    }
}
