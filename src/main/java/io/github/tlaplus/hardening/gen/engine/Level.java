package io.github.tlaplus.hardening.gen.engine;

/**
 * The TLA+ level of the formula an expression form builds, as far as generation distinguishes it.
 *
 * <p>Constant and state level are not separated: generation never needs a constant-level context.
 * A temporal formula that contains an action is kept apart from one that does not, because both
 * checkers accept an action inside a temporal formula only in a few forms; see
 * {@link LevelContext}.
 */
public enum Level {
    /** A constant or state-level expression, including {@code ENABLED}. */
    STATE,
    /** An expression that may mention primed variables. */
    ACTION,
    /** A temporal formula whose subformulas are not actions. */
    TEMPORAL,
    /** A temporal formula built around an action, such as {@code WF_v(A)} or {@code []<><<A>>_v}. */
    ACTION_TEMPORAL
}
