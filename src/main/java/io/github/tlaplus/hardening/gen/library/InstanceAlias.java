package io.github.tlaplus.hardening.gen.library;

import java.util.Objects;

/**
 * A library name the TLA+ source defines as {@code name(p1, ..., pn) == I!Operator(p1, ..., pn)},
 * where {@code I} is the named instance of the target's module.
 */
public record InstanceAlias(String name, OperatorId target, int arity) {
    public InstanceAlias {
        OperatorId.requireIdentifier(name);
        Objects.requireNonNull(target, "target");
        if (arity < 0) throw new IllegalArgumentException("negative arity");
    }

    /** The named instance through which this alias calls its target. */
    public String instance() {
        return OperatorLibrary.instanceName(target.module());
    }
}
