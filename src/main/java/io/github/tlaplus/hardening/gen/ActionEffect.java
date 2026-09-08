package io.github.tlaplus.hardening.gen;

import java.util.List;
import java.util.Set;

/** Nonempty, duplicate-free effect in variable declaration order, with set-style identity. */
public final class ActionEffect {
    private final List<String> variables;
    private final Set<String> key;

    public ActionEffect(List<String> variables) {
        this.variables = List.copyOf(variables);
        this.key = Set.copyOf(variables);
        if (variables.isEmpty() || key.size() != variables.size()) {
            throw new IllegalArgumentException("an action effect requires distinct variables and is nonempty");
        }
    }

    public List<String> variables() {
        return variables;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ActionEffect effect && key.equals(effect.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return variables.toString();
    }
}
