package io.github.tlaplus.hardening.gen.engine;

/**
 * The TLA+ level of the formula an expression form builds, as <em>Specifying Systems</em> defines
 * it and SANY checks it.
 *
 * <p>Constant and state level are not separated: generation never needs a constant-level context.
 */
public enum Level {
    /** A constant or state-level expression, including {@code ENABLED}. */
    STATE,
    /** An expression that may mention primed variables. */
    ACTION,
    /** A temporal formula, including {@code [][A]_v}, {@code <><<A>>_v}, {@code WF} and {@code SF}. */
    TEMPORAL
}
