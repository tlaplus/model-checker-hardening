package io.github.tlaplus.hardening.gen.trace;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.common.Preconditions;

/**
 * What the last state of a recorded trace claims (ADR 0034 §1), which decides what its replay
 * checks there.
 */
@ArchRole(Role.MODEL)
@Key
public sealed interface TraceEnding {
    /** The last state violates the invariant {@code Inv}. */
    record Invariant() implements TraceEnding {}

    /**
     * The trace is a lasso: its last state equals the state at {@code index}, from which the
     * behavior continues.
     */
    record Loop(int index) implements TraceEnding {
        public Loop {
            Preconditions.requireNonnegative(index, "index");
        }
    }

    /** No claim beyond reachability, as for a violation of a safety part of a property. */
    record Prefix() implements TraceEnding {}
}
