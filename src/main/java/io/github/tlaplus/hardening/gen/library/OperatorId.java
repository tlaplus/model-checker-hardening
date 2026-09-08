package io.github.tlaplus.hardening.gen.library;

import java.util.Objects;

/** Case-sensitive identity of an exported operator, independent of its generated name. */
public record OperatorId(String module, String operator) {
    public OperatorId {
        requireIdentifier(module);
        requireIdentifier(operator);
    }

    public static void requireIdentifier(String name) {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[A-Za-z_][A-Za-z_0-9]*")) {
            throw new IllegalArgumentException("expected a TLA+ identifier, found '" + name + "'");
        }
    }

    public static OperatorId parse(String text) {
        var parts = text.split("!", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected Module!Operator, found '" + text + "'");
        }
        return new OperatorId(parts[0], parts[1]);
    }

    @Override
    public String toString() {
        return module + "!" + operator;
    }
}
