package io.github.tlaplus.hardening.gen.engine;

import java.util.EnumSet;
import java.util.Set;

/**
 * The levels an expression request may produce, which {@link GenerationContext} keeps as a dynamic
 * ceiling while a body is drawn.
 *
 * <p>A form whose {@link Level} the context does not admit is not selectable. An ordinary operand
 * is drawn in {@link #valueOperand()}; the forms that TLA+ and both checkers allow to pass a
 * temporal context through, such as the Boolean connectives, draw their operands in the same
 * context instead, and the level-changing forms name the context of each operand explicitly.
 * ADR 0007 records the checker measurements behind these tables.
 */
enum LevelContext {
    /** Constant and state-level expressions. The default for every body. */
    STATE(Level.STATE),
    /** Expressions that may prime variables. */
    ACTION(Level.STATE, Level.ACTION),
    /** A temporal property formula. */
    TEMPORAL(Level.STATE, Level.TEMPORAL, Level.ACTION_TEMPORAL),
    /**
     * A temporal formula that may not contain an action, as under {@code []}, {@code <>} and
     * {@code ~>}. TLC checks an action inside a temporal formula only when Boolean connectives alone
     * separate it from the top of the formula, and Apalache fails its assignment analysis on an
     * action under {@code ~>}.
     */
    ACTION_FREE_TEMPORAL(Level.STATE, Level.TEMPORAL);

    private final Set<Level> admitted;

    LevelContext(Level first, Level... rest) {
        admitted = EnumSet.of(first, rest);
    }

    /** Reports whether a form of this level may be selected in this context. */
    boolean admits(Level level) {
        return admitted.contains(level);
    }

    /**
     * Returns the context of an operand that is not a Boolean passed through a transparent form.
     *
     * <p>Such an operand is a value, a quantifier body, a predicate or a domain. None of them may be
     * temporal: TLA+ forbids it for values, and Apalache crashes on a quantifier over a temporal
     * body. A primed value stays admissible in an action context, as in {@code x' + 1}.
     */
    LevelContext valueOperand() {
        return this == ACTION ? ACTION : STATE;
    }
}
