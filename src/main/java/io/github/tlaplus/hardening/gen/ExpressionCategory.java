package io.github.tlaplus.hardening.gen;

/** A user-facing category assigned to generated expression forms. */
public enum ExpressionCategory {
    ACTION("action"),
    TEMPORAL("temporal"),
    UNBOUND("unbound"),
    EXOTIC("exotic"),
    CORE("core", false),
    CONTROL("control"),
    LABEL("label"),
    OPERATOR("operator"),
    QUANTIFIER("quantifier"),
    BOOL_LOGIC("bool_logic"),
    ARITHMETIC("arithmetic"),
    SET("set"),
    FINITE_SET("finite_set"),
    UNIVERSE("universe"),
    SEQUENCE("sequence"),
    FUNCTION("function"),
    FOLD("fold"),
    TUPLE("tuple"),
    RECORD("record"),
    VARIANT("variant"),
    MODEL("model");

    private final String configName;
    private final boolean ignorable;

    ExpressionCategory(String configName) {
        this(configName, true);
    }

    ExpressionCategory(String configName, boolean ignorable) {
        this.configName = configName;
        this.ignorable = ignorable;
    }

    /** Returns the lowercase name used in {@code generator.ignore}. */
    public String configName() {
        return configName;
    }

    /** Reports whether this category may appear in {@code generator.ignore}. */
    public boolean isIgnorable() {
        return ignorable;
    }
}
