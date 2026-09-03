package io.github.tlaplus.hardening.gen;

/**
 * An expression form whose share of nonterminal selection is configurable.
 *
 * <p>Selection is otherwise uniform over the forms applicable to a request, which spends the same
 * probability on a form that consumes the surrounding lexical context as on any other. A generated
 * lambda that never mentions its parameters, or a membership test whose right-hand side is always
 * the empty set, is well-formed but says nothing a model checker has to work for. A weight is how
 * many slots a form occupies, so a weight of {@code n} makes it {@code n} times as likely as an
 * unweighted form applicable to the same request.
 *
 * <p>This enum is the user-facing name of a form. The catalog of forms itself is internal to the
 * generator engine and is not configuration surface; only the forms listed here accept a weight.
 */
public enum ExpressionForm {
    /** The closed leaf of a request, which prefers a visible binding of the requested type. */
    TERMINAL("terminal"),
    /** A reference to a visible binding. */
    NAME("name"),
    /** An application of a visible operator. */
    OPERATOR_APPLICATION("operator_application"),
    /** A non-empty set literal. */
    ENUM_SET("enum_set");

    /** Slots a form occupies when its weight is not configured. */
    public static final int DEFAULT_WEIGHT = 1;

    private final String configName;

    ExpressionForm(String configName) {
        this.configName = configName;
    }

    /** Returns the lowercase name used in {@code generator.weights}. */
    public String configName() {
        return configName;
    }
}
