package io.github.tlaplus.hardening.architecture.model;

/**
 * How one type refers to another in the architecture atlas (ADR 0022).
 *
 * <p>Declaration order is strength order: when a type refers to another in several ways, the
 * atlas keeps the first kind in this order.
 */
public enum RelationKind {
    EXTENDS("extends", "a declared superclass, or a superinterface of an interface"),
    IMPLEMENTS("implements", "a declared superinterface of a class, enum, or record"),
    HAS("has", "an instance field or record component, possibly inside type arguments"),
    USES("uses", "a constructor or method signature, or a static field"),
    DEPENDS("depends", "only a method body, as jdeps reports it");

    private final String label;
    private final String description;

    RelationKind(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** The word the diagrams print on an arrow. */
    public String label() {
        return label;
    }

    /** One line for the legend. */
    public String description() {
        return description;
    }

    /** The stronger of two kinds. */
    public RelationKind strongest(RelationKind other) {
        return compareTo(other) <= 0 ? this : other;
    }
}
