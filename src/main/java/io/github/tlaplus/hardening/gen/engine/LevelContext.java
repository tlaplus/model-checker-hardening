package io.github.tlaplus.hardening.gen.engine;

import java.util.EnumSet;
import java.util.Set;

/**
 * The levels an expression request may produce, which {@link GenerationContext} keeps as a dynamic
 * ceiling while a body is drawn.
 *
 * <p>A form whose {@link Level} the context does not admit is not selectable. An ordinary operand
 * is drawn in {@link #valueOperand()}; the forms through which TLA+ passes a temporal formula, such
 * as the Boolean connectives and quantifier bodies, draw their operands in the same context
 * instead, and the level-changing forms name the context of each operand explicitly. The rules
 * are those of <em>Specifying Systems</em> as SANY checks them; the shapes a model checker cannot
 * handle are rejected by known-defect signatures, not here (ADR 0007).
 */
enum LevelContext {
    /** Constant and state-level expressions. The default for every body. */
    STATE(Level.STATE),
    /** Expressions that may prime variables. */
    ACTION(Level.STATE, Level.ACTION),
    /**
     * A temporal formula. It admits no action: an action occurs in a temporal formula only inside
     * {@code [][A]_v}, {@code <><<A>>_v}, {@code WF_v(A)} or {@code SF_v(A)}, which draw it in
     * {@link #ACTION}.
     */
    TEMPORAL(Level.STATE, Level.TEMPORAL);

    private final Set<Level> admitted;

    LevelContext(Level first, Level... rest) {
        admitted = EnumSet.of(first, rest);
    }

    /** Reports whether a form of this level may be selected in this context. */
    boolean admits(Level level) {
        return admitted.contains(level);
    }

    /**
     * Returns the context of an operand that is not a Boolean passed on at the form's own level.
     *
     * <p>Such an operand is a value, a predicate, a domain, a set or function body, or a
     * {@code CHOOSE} body. None of them may be temporal: SANY rejects a temporal operand of
     * {@code =}, of a tuple, of a set filter and of {@code CHOOSE}, and accepts a temporal function
     * body or {@code IF} condition although neither is a meaningful formula. A primed value stays
     * admissible in an action context, as in {@code x' + 1}.
     */
    LevelContext valueOperand() {
        return this == ACTION ? ACTION : STATE;
    }
}
