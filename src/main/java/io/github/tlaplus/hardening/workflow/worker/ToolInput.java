package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Objects;

/**
 * One rendered specification and the exploration depth it asks for.
 *
 * <p>The depth belongs to the artifact rather than to the stage: an expression input has a single
 * state and asks for zero transitions, while a generated module bounds its own step counter. Only
 * a checker that needs the bound as a parameter reads it — Apalache unrolls exactly this many
 * transitions, whereas the parser has no notion of it and TLC is bounded instead by the module's
 * own state constraint.
 *
 * @param text the specification in whatever representation the receiving tool consumes
 * @param length transitions a bounded checker should explore, never negative
 */
public record ToolInput(String text, int length) {
    public ToolInput {
        Objects.requireNonNull(text, "text");
        Preconditions.requireNonnegative(length, "length");
    }
}
