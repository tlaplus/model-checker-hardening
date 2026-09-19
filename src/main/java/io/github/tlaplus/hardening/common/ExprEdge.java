package io.github.tlaplus.hardening.common;

import java.util.Comparator;
import java.util.Objects;

/**
 * An edge of an expression tree between two constructs: {@code parent} applied to, or binding, an
 * immediate subexpression that is the construct {@code child}. Both are construct names as in
 * {@link ExprCounts#exprs()}.
 */
public record ExprEdge(String parent, String child) implements Comparable<ExprEdge> {
    private static final Comparator<ExprEdge> ORDER =
            Comparator.comparing(ExprEdge::parent).thenComparing(ExprEdge::child);

    public ExprEdge {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(child, "child");
    }

    @Override
    public int compareTo(ExprEdge other) {
        return ORDER.compare(this, other);
    }
}
