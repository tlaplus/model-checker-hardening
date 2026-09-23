package io.github.tlaplus.hardening.gen.library;

import java.util.Objects;

/**
 * A library name the TLA+ source defines as {@code name(p1, ..., pn) == I!Operator(p1, ..., pn)},
 * where {@code I} is the named instance of {@code sourceModule}: the target's module, or the
 * separate TLC module of a diff-linked one.
 */
public record InstanceAlias(String name, OperatorId target, String sourceModule, int arity) {
    public InstanceAlias {
        OperatorId.requireIdentifier(name);
        Objects.requireNonNull(target, "target");
        OperatorId.requireIdentifier(sourceModule);
        if (arity < 0) throw new IllegalArgumentException("negative arity");
    }

    /** The named instance through which this alias calls its target. */
    public String instance() {
        return OperatorLibrary.instanceName(target.module());
    }
}
