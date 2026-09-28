package io.github.tlaplus.hardening.gen.rewrite;

import java.util.List;
import java.util.Objects;

/**
 * A decoded metamorphic pair: the base the payload's base part decodes to, its rewrite, the
 * orientation, and the rules applied in order.
 */
public record Rewrite<T>(T original, T rewritten, Orientation orientation, List<String> appliedRules) {
    public Rewrite {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(rewritten, "rewritten");
        Objects.requireNonNull(orientation, "orientation");
        appliedRules = List.copyOf(Objects.requireNonNull(appliedRules, "appliedRules"));
    }

    /** Whether no rule applied, so both sides are the same module. */
    public boolean isIdentity() {
        return appliedRules.isEmpty();
    }

    /** Returns the side the checker explores. */
    public T explored() {
        return orientation.explored(original, rewritten);
    }

    /** Returns the side the checker checks. */
    public T checked() {
        return orientation.checked(original, rewritten);
    }
}
