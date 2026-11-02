package io.github.tlaplus.hardening.workflow.worker;

import java.io.IOException;
import java.util.List;

/**
 * What one checker invocation checks in an assembled module: which side's specification it
 * explores, and whether it checks the invariant, the action invariant of a metamorphic module and a
 * temporal property. The tool adapters name the module's entry points accordingly.
 *
 * <p>A metamorphic module whose properties TLC checks apart (ADR 0027 §4) is checked by three TLC
 * invocations: {@link #RELATION_AND_PROPERTIES}, then its {@link #PROPERTY_RUNS}.
 */
public enum CheckKind {
    /** The invariant, as for an expression or a module without a property. */
    INVARIANT(0, Side.EXPLORED, true, false, false),
    /** The invariant and the property under the specification's fairness. */
    INVARIANT_AND_PROPERTY(1, Side.EXPLORED, true, false, true),
    /** The relation of a metamorphic module: its invariant and its action invariant (ADR 0016 §3). */
    RELATION(2, Side.EXPLORED, true, true, false),
    /** The relation, which TLC follows with the {@link #PROPERTY_RUNS} (ADR 0027 §4). */
    RELATION_AND_PROPERTIES(3, Side.EXPLORED, true, true, false),
    /** {@code SpecE ⊨ PropE}: the explored side's property only. */
    EXPLORED_PROPERTY(4, Side.EXPLORED, false, false, true),
    /** {@code SpecC ⊨ PropC}: the checked side's property under the checked side's specification. */
    CHECKED_PROPERTY(5, Side.CHECKED, false, false, true);

    /** The runs after {@link #RELATION_AND_PROPERTIES}, in the order they run. */
    public static final List<CheckKind> PROPERTY_RUNS = List.of(EXPLORED_PROPERTY, CHECKED_PROPERTY);

    /** Which side of a metamorphic module an invocation explores; every other module has one side. */
    public enum Side {
        /** The module's own specification, {@code Spec}. */
        EXPLORED,
        /** The checked side's specification of a metamorphic module, {@code SpecC}. */
        CHECKED
    }

    private final int protocolCode;
    private final Side side;
    private final boolean invariant;
    private final boolean actionInvariant;
    private final boolean property;

    CheckKind(int protocolCode, Side side, boolean invariant, boolean actionInvariant, boolean property) {
        this.protocolCode = protocolCode;
        this.side = side;
        this.invariant = invariant;
        this.actionInvariant = actionInvariant;
        this.property = property;
    }

    /** Returns the side whose specification the invocation explores. */
    public Side side() {
        return side;
    }

    /** Whether the invocation checks the state invariant. */
    public boolean invariant() {
        return invariant;
    }

    /** Whether the invocation checks the action invariant of a metamorphic module on every transition. */
    public boolean actionInvariant() {
        return actionInvariant;
    }

    /** Whether the invocation checks its side's temporal property. */
    public boolean property() {
        return property;
    }

    /** Whether TLC follows this invocation with the {@link #PROPERTY_RUNS}. */
    public boolean hasPropertyRuns() {
        return this == RELATION_AND_PROPERTIES;
    }

    int protocolCode() {
        return protocolCode;
    }

    static CheckKind fromProtocolCode(int code) throws IOException {
        for (var kind : values()) {
            if (kind.protocolCode == code) {
                return kind;
            }
        }
        throw new IOException("invalid worker request kind: " + code);
    }
}
