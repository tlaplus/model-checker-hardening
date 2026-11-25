package io.github.tlaplus.hardening.gen.trace;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import java.util.List;
import java.util.Objects;

/**
 * The states of one recorded counterexample and what its last state claims (ADR 0034 §1, §2).
 *
 * <p>Each state lists one value per state variable of the base module, in declaration order. The
 * values are CBOR items in the canonical, type-directed form of ADR 0034 §2: what an item means
 * follows from the type of its variable, which only the decoded base module knows. A trace of n + 1
 * states has n transitions.
 *
 * @param states the states s0, ..., sn, each of the same width
 * @param ending the claim about sn; a loop index lies below n
 */
@ArchRole(Role.MODEL)
@Key
public record Trace(List<List<JsonNode>> states, TraceEnding ending) {
    public Trace {
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(ending, "ending");
        Preconditions.require(!states.isEmpty(), "a trace has at least one state");
        var width = states.getFirst().size();
        Preconditions.require(width > 0, "a state has at least one variable");
        states = states.stream().map(state -> {
            Preconditions.require(state.size() == width, "every state has the same variables");
            return state.stream().map(value -> (JsonNode) Objects.requireNonNull(value, "value").deepCopy()).toList();
        }).toList();
        if (ending instanceof TraceEnding.Loop(var index)) {
            Preconditions.require(index < states.size() - 1, "a loop returns to a state before the last");
        }
    }

    /** Returns the number of transitions, one fewer than the states. */
    public int transitions() {
        return states.size() - 1;
    }

    /** Returns the number of state variables. */
    public int width() {
        return states.getFirst().size();
    }
}
