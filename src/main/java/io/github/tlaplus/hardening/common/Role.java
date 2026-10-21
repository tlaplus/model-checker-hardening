package io.github.tlaplus.hardening.common;

/**
 * What a type does in its package, as the architecture atlas shows it (ADR 0022).
 *
 * <p>A type's role is what its callers rely on, not its shape: a record that other packages call
 * is a {@link #FACADE}, not a {@link #MODEL}.
 */
public enum Role {
    ENTRY_POINT("a command or a process main"),
    FACADE("the type other packages call"),
    CONTRACT("an interface or base type that other types implement"),
    PROCESS("a loop or orchestrator that owns threads or a lifecycle"),
    ADAPTER("wraps an external tool, process, or protocol"),
    STORE("layout, locking, persistence"),
    CODEC("a wire or storage format"),
    POLICY("a decision rule"),
    MODEL("a domain value or enum"),
    CONFIG("configuration and its schema"),
    VIEW("rendering and formatting"),
    UTILITY("a helper with no domain meaning");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    /** One line that the atlas shows as the role's legend. */
    public String description() {
        return description;
    }
}
