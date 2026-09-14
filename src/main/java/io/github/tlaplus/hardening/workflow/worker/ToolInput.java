package io.github.tlaplus.hardening.workflow.worker;

import java.util.Objects;

/**
 * One rendered specification and what the receiving checker is asked about it.
 *
 * <p>The request belongs to the artifact rather than to the stage: an expression input has a single
 * state and asks for no transitions, while a generated module bounds its own step counter and may
 * carry a temporal property.
 *
 * @param text the specification in whatever representation the receiving tool consumes
 * @param request the exploration bound and the properties to check
 */
public record ToolInput(String text, CheckRequest request) {
    public ToolInput {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(request, "request");
    }

    /** Returns an input whose checkers are asked only about the invariant. */
    public ToolInput(String text, int transitions) {
        this(text, CheckRequest.invariant(transitions));
    }
}
